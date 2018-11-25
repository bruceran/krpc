package krpc.rpc.monitor;

import ch.qos.logback.classic.Level;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import krpc.common.*;
import krpc.rpc.core.DumpPlugin;
import krpc.rpc.core.HealthPlugin;
import krpc.rpc.core.HealthStatus;
import krpc.rpc.core.RefreshPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Sharable
public class SelfCheckHttpServer extends ChannelDuplexHandler implements InitClose, StartStop, AlarmAware {

    static Logger log = LoggerFactory.getLogger(SelfCheckHttpServer.class);

    int port = 9600;
    String host = "*";
    int idleSeconds = 15;
    int maxConns = 10000;
    int workerThreads = 1;
    int backlog = 128;

    int maxInitialLineLength = 4096;
    int maxHeaderSize = 8192;
    int maxChunkSize = 8192;
    int maxContentLength = 1000000;

    String versionString;

    NamedThreadFactory bossThreadFactory = new NamedThreadFactory("krpc_selfcheck_boss");
    NamedThreadFactory workThreadFactory = new NamedThreadFactory("krpc_selfcheck_worker");

    EventLoopGroup bossGroup;
    EventLoopGroup workerGroup;
    Channel serverChannel;

    ConcurrentHashMap<String, Channel> conns = new ConcurrentHashMap<String, Channel>();

    AtomicBoolean stopFlag = new AtomicBoolean();

    ServerBootstrap serverBootstrap;

    List<DumpPlugin> dumpPlugins = new ArrayList<>();
    List<HealthPlugin> healthPlugins = new ArrayList<>();
    List<RefreshPlugin> refreshPlugins = new ArrayList<>();

    Alarm alarm;

    public SelfCheckHttpServer() {
    }

    public SelfCheckHttpServer(int port) {
        this.port = port;
    }

    public void init() {

        bossGroup = new NioEventLoopGroup(1, bossThreadFactory);
        workerGroup = new NioEventLoopGroup(workerThreads, workThreadFactory);

        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("timeout", new IdleStateHandler(0, 0, idleSeconds));
                        pipeline.addLast("codec", new HttpServerCodec(maxInitialLineLength, maxHeaderSize, maxChunkSize));
                        pipeline.addLast("aggregator", new HttpObjectAggregator(maxContentLength));
                        pipeline.addLast("compressor", new HttpContentCompressor());
                        pipeline.addLast("handler", SelfCheckHttpServer.this);
                    }
                });
        serverBootstrap.option(ChannelOption.SO_REUSEADDR, true);
        serverBootstrap.option(ChannelOption.SO_BACKLOG, backlog);
        serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        // serverBootstrap.childOption(ChannelOption.SO_RCVBUF, 65536);
    }

    public void start() {
        InetSocketAddress addr = null;
        if (host == null || "*".equals(host)) {
            addr = new InetSocketAddress(port); // "0.0.0.0",
        } else {
            addr = new InetSocketAddress(host, port);
        }
        try {
            serverChannel = serverBootstrap.bind(addr).syncUninterruptibly().channel();
            log.info("selfcheck server started on host(" + host + ") port(" + port + ")");
        } catch(Exception e) {
            log.error("selfcheck server bind exception, port="+port);
            System.exit(-1);
        }
    }

    public void close() {

        if (workerGroup != null) {

            log.info("stopping selfcheck server");

            bossGroup.shutdownGracefully();
            bossGroup = null;

            ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            allChannels.add(serverChannel);
            for (Channel ch : conns.values()) {
                allChannels.add(ch);
            }
            ChannelGroupFuture future = allChannels.close();
            future.awaitUninterruptibly();

            workerGroup.shutdownGracefully();
            workerGroup = null;

            log.info("selfcheck server stopped");
        }
    }

    String getConnId(ChannelHandlerContext ctx) {
        Channel ch = ctx.channel();
        return parseIpPort(ch.remoteAddress().toString()) + ":" + ch.id().asShortText();
    }

    public void stop() {
        stopFlag.set(true);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        if (stopFlag.get()) {
            ctx.close();
            return;
        }

        String connId = getConnId(ctx);

        if (conns.size() >= maxConns) {
            ctx.close();
            log.error("connection started, connId={}, but max connections exceeded, conn not allowed", connId);
            return;
        }

        log.info("connection started, connId={}", connId);

        conns.put(connId, ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String connId = getConnId(ctx);
        conns.remove(connId);
        log.info("connection ended, connId={}", connId);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.ALL_IDLE) {
                String connId = getConnId(ctx);
                ctx.close();
                log.error("connection timeout, connId={}", connId);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String connId = getConnId(ctx);
        if(log.isDebugEnabled()) {
            log.debug("connection exception, connId="+connId+",msg="+cause.toString(),cause);
        }
        ctx.close();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {

        try {
            FullHttpRequest httpReq = (FullHttpRequest) msg;

            if (!httpReq.decoderResult().isSuccess()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST, "decode error");
                return;
            }

            if (httpReq.method() != HttpMethod.GET) {
                sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "not allowed");
                return;
            }

            String uri = httpReq.uri();
            int p1 = findPathEndIndex(uri);
            String path = p1 >= 0 ? uri.substring(0, p1) : uri;
            if( path.endsWith("/") ) path = path.substring(0,path.length()-1);

            if( path.equalsIgnoreCase("/health")) {
                Map<String,Object>  values = doHealth();
                sendResponse(ctx,HttpResponseStatus.OK,values);
                return;
            }

            if( path.equalsIgnoreCase("/dump")) {
                Map<String,Object>  values = doDump();

                log.info("----- start dump -----");
                for(Map.Entry<String,Object> entry: values.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    log.info("key="+key+", value="+value);
                }
                log.info("----- end dump -----");

                sendResponse(ctx,HttpResponseStatus.OK,values);
                return;
            }

            if( path.equalsIgnoreCase("/refresh")) {
                Map<String,Object>  values = doRefresh();
                sendResponse(ctx,HttpResponseStatus.OK,values);
                return;
            }

            if( path.equalsIgnoreCase("/alive")) {
                Map<String,Object>  values = doAlive();
                sendResponse(ctx,HttpResponseStatus.OK,values);
                return;
            }

            if( path.startsWith("/logger")) {
                Map<String,Object> values = doLogger(path);
                sendResponse(ctx,HttpResponseStatus.OK,values);
                return;
            }

            sendError(ctx, HttpResponseStatus.OK, "unknown uri");

        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    Map<String,Object> doLogger(String path) {

        Map<String,Object> values = new LinkedHashMap<>();
        values.put("result","done" );

        path = path.substring(1);
        String[] ss = path.split("/");
        if( ss.length != 2 && ss.length != 3) {
            values.put("result","failed" );
            values.put("reason","url format not valid" );
            return values;
        }
        String packageName = ss[1];
        if( ss.length == 2 ) {
            values.put("action","query log level" );
            values.put("level",queryLoggerLevel(packageName) );
            return values;
        }

        Level level = toLevel(ss[2]);
        if( level == null ) {
            values.put("result","failed" );
            values.put("action","change log level" );
            values.put("reason","level not valid" );
            return values;
        }
        changeLoggerLevel(level,packageName);
        values.put("action","change log level" );
        values.put("level",queryLoggerLevel(packageName) );
        return values;
    }

    String queryLoggerLevel(String packageName) {
        try {
            Logger log = LoggerFactory.getLogger(packageName);
            if (log != null && log instanceof ch.qos.logback.classic.Logger) {
                ch.qos.logback.classic.Logger log2 = ((ch.qos.logback.classic.Logger) log);
                return log2.getLevel().toString();
            }
        } catch(Exception e) {
        }
        return "unknown";
    }

    void changeLoggerLevel(Level level,String packageName) {
        Logger log = LoggerFactory.getLogger(packageName);
        if( log instanceof ch.qos.logback.classic.Logger ) {
            ch.qos.logback.classic.Logger log2 = ((ch.qos.logback.classic.Logger) log);
            log2.setLevel(level);
        }
    }

    public Level toLevel(String sArg) {
        if (sArg == null) {
            return null;
        } else if (sArg.equalsIgnoreCase("ALL")) {
            return Level.ALL;
        } else if (sArg.equalsIgnoreCase("TRACE")) {
            return Level.TRACE;
        } else if (sArg.equalsIgnoreCase("DEBUG")) {
            return Level.DEBUG;
        } else if (sArg.equalsIgnoreCase("INFO")) {
            return Level.INFO;
        } else if (sArg.equalsIgnoreCase("WARN")) {
            return Level.WARN;
        } else if (sArg.equalsIgnoreCase("ERROR")) {
            return Level.ERROR;
        } else {
            return sArg.equalsIgnoreCase("OFF") ? Level.OFF : null;
        }
    }

    Map<String,Object> doHealth() {
        Map<String,Object> values = new LinkedHashMap<>();

        boolean ok = true;
        List<HealthStatus> all = new ArrayList<>();
        for(HealthPlugin p: healthPlugins) {
            try {
                p.healthCheck(all);
            } catch(Exception e) {
                log.error("health check exception, message="+e.getMessage(),e);
                ok = false;
            }
        }
        if( all.size() == 0 ) {
            String alarmId = alarm.getAlarmId("000");
            all.add(new HealthStatus(alarmId,true,"everything is ok"));
        }
        values.put("result",ok ? "done" : "exception");
        values.put("details", all);
        return values;
    }

    public Map<String,Object> doDump() {
        Map<String,Object> values = new LinkedHashMap<>();
        if( versionString != null ) values.put("krpc.version",versionString);

        SystemDump.dumpSystemProperties(values);

        boolean ok = true;
        for(DumpPlugin p: dumpPlugins) {
            try {
                p.dump(values);
            } catch(Exception e) {
                log.error("dump exception, message="+e.getMessage(),e);
                ok = false;
            }
        }

        values.put("result",ok ? "done" : "exception");
        return values;
    }

    Map<String,Object> doRefresh() {

        boolean ok = true;
        for(RefreshPlugin p: refreshPlugins) {
            try {
                p.refresh();
            } catch(Exception e) {
                log.error("refresh exception, message="+e.getMessage(),e);
                ok = false;
            }
        }

        Map<String,Object> values = new LinkedHashMap<>();
        values.put("result",ok ? "done" : "exception");
        return values;
    }

    Map<String,Object> doAlive() {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("result","alive");
        return values;
    }

    void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String retMsg ) {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("status",retMsg);
        sendResponse(ctx,status,values);
    }

    void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, Map<String,Object> values) {
        String json = Json.toJson(values);
        ByteBuf bb = ctx.alloc().buffer(json.length()*2);
        bb.writeCharSequence(json, CharsetUtil.UTF_8);
        int len = bb.readableBytes();
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, bb);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, len);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    int findPathEndIndex(String uri) {
        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char c = uri.charAt(i);
            if (c == '?' || c == '#') {
                return i;
            }
        }
        return -1;
    }

    String parseIpPort(String s) {
        int p = s.indexOf("/");

        if (p >= 0)
            return s.substring(p + 1);
        else
            return s;
    }

    public void addDumpPlugin(DumpPlugin p) {
        dumpPlugins.add(p);
    }
    public void addHealthPlugin(HealthPlugin p) {
        healthPlugins.add(p);
    }
    public void addRefreshPlugin(RefreshPlugin p) {
        refreshPlugins.add(p);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getIdleSeconds() {
        return idleSeconds;
    }

    public void setIdleSeconds(int idleSeconds) {
        this.idleSeconds = idleSeconds;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public int getMaxConns() {
        return maxConns;
    }

    public void setMaxConns(int maxConns) {
        this.maxConns = maxConns;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    public void setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
    }

    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    public void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public String getVersionString() {
        return versionString;
    }

    public void setVersionString(String versionString) {
        this.versionString = versionString;
    }

    @Override
    public void setAlarm(Alarm alarm) {
        this.alarm = alarm;
    }
}
