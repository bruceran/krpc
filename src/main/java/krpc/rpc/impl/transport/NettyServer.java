package krpc.rpc.impl.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import krpc.common.InitClose;
import krpc.common.NamedThreadFactory;
import krpc.common.StartStop;
import krpc.rpc.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Sharable
public class NettyServer extends TransportBase implements Transport, InitClose, StartStop , DumpPlugin {

    static Logger log = LoggerFactory.getLogger(NettyServer.class);

    int port = 5600;
    String host = "*";
    int idleSeconds = 180;
    int maxPackageSize = 1000000;
    int maxConns = 500000;
    int workerThreads = 0;
    int backlog = 300;
    boolean nativeNetty = false;

    NamedThreadFactory bossThreadFactory = new NamedThreadFactory("krpc_nettyserver_boss");
    NamedThreadFactory workThreadFactory = new NamedThreadFactory("krpc_nettyserver_worker");

    EventLoopGroup bossGroup;
    EventLoopGroup workerGroup;
    Channel serverChannel;

    ConcurrentHashMap<String, Channel> conns = new ConcurrentHashMap<String, Channel>();

    ServerBootstrap serverBootstrap;

    public NettyServer() {
    }

    public NettyServer(int port, TransportCallback callback, RpcCodec codec, ServiceMetas serviceMetas) {
        this.callback = callback;
        this.codec = codec;
        this.serviceMetas = serviceMetas;
        this.port = port;
    }

    public void init() {

        int processors = Runtime.getRuntime().availableProcessors();
        if( workerThreads == 0 ) workerThreads = processors;

        String osName = System.getProperty("os.name");
        if( nativeNetty && osName != null && osName.toLowerCase().indexOf("linux") >= 0 ) {
            nativeNetty = true;
        } else {
            nativeNetty = false;
        }

        if( nativeNetty) {
            bossGroup = new EpollEventLoopGroup(1, bossThreadFactory);
            workerGroup = new EpollEventLoopGroup(workerThreads, workThreadFactory);
        } else {
            bossGroup = new NioEventLoopGroup(1, bossThreadFactory);
            workerGroup = new NioEventLoopGroup(workerThreads, workThreadFactory);
        }
        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("frame-decoder", new LengthFieldBasedFrameDecoder(maxPackageSize, 4, 4, 0, 0));
                        pipeline.addLast("timeout", new IdleStateHandler(0, 0, idleSeconds));
                        pipeline.addLast("handler", NettyServer.this);
                    }
                });
        serverBootstrap.option(ChannelOption.SO_BACKLOG, backlog);
        serverBootstrap.option(ChannelOption.SO_REUSEADDR, true);
        serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        // serverBootstrap.childOption(ChannelOption.SO_RCVBUF, 65536);
    }

    public void start() {
        InetSocketAddress addr = null;
        if (host == null || "*".equals(host)) {
            addr = new InetSocketAddress(port);
        } else {
            addr = new InetSocketAddress(host, port);
        }
        try {
            serverChannel = serverBootstrap.bind(addr).syncUninterruptibly().channel();
            log.info("netty server started on host(" + host + ") port(" + port + ")");
        } catch(Exception e) {
            log.error("netty server bind exception, port="+port);
            System.exit(-1);
        }
    }

    public void close() {

        if (workerGroup != null) {

            log.info("stopping netty server");

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

            log.info("netty server stopped");
        }
    }

    boolean isServerSide() {
        return true;
    }

    Channel getChannel(String connId) {
        Channel o = conns.get(connId);
        if (o == null)
            return null;
        return o;
    }

    String getConnId(ChannelHandlerContext ctx) {
        Channel ch = ctx.channel();
        return parseIpPort(ch.remoteAddress().toString()) + ":" + ch.id().asShortText();
    }

    public void disconnect(String connId) {
        Channel ch = getChannel(connId);
        if (ch == null) {
            log.error("connection not found, connId={}", connId);
            return;
        }
        ch.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        //debugLog("channelActive");
        if (stopFlag.get()) {
            ctx.close();
            return;
        }

        String connId = getConnId(ctx);

        if (conns.size() >= maxConns) {
            log.error("connection started, connId={}, but max connections exceeded, conn not allowed", connId);
            ctx.close();
            return;
        }

        log.info("connection started, connId={}", connId);

        conns.put(connId, ctx.channel());
        if (callback != null)
            callback.connected(connId, parseIpPort(ctx.channel().localAddress().toString()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //debugLog("channelInactive");
        String connId = getConnId(ctx);
        conns.remove(connId);
        if( enableEncrypt) {
            keyMap.remove(connId);
        }
        log.info("connection ended, connId={}", connId);
        if (callback != null)
            callback.disconnected(connId);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        //debugLog("userEventTriggered, evt="+evt);
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.ALL_IDLE) {
                String connId = getConnId(ctx);
                log.error("connection timeout, connId={}", connId);
                ctx.close();
            }
        }
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

    public int getMaxPackageSize() {
        return maxPackageSize;
    }

    public void setMaxPackageSize(int maxPackageSize) {
        this.maxPackageSize = maxPackageSize;
    }

    public int getMaxConns() {
        return maxConns;
    }

    public void setMaxConns(int maxConns) {
        this.maxConns = maxConns;
    }

    public int getIdleSeconds() {
        return idleSeconds;
    }

    public void setIdleSeconds(int idleSeconds) {
        this.idleSeconds = idleSeconds;
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

    public boolean isNativeNetty() {
        return nativeNetty;
    }

    public void setNativeNetty(boolean nativeNetty) {
        this.nativeNetty = nativeNetty;
    }

    @Override
    public void dump(Map<String, Object> metrics) {
        if( nativeNetty) {
            metrics.put("krpc.server.nativeNetty", true);
            metrics.put("krpc.server.boss.threads", ((EpollEventLoopGroup)bossGroup).executorCount());
            metrics.put("krpc.server.worker.threads",  ((EpollEventLoopGroup)workerGroup).executorCount());
        } else {
            metrics.put("krpc.server.boss.threads", ((NioEventLoopGroup)bossGroup).executorCount());
            metrics.put("krpc.server.worker.threads",  ((NioEventLoopGroup)workerGroup).executorCount());
        }
        metrics.put("krpc.server.conns.size",conns.size());

        metrics.put("krpc.server.port",port);
        metrics.put("krpc.server.host",host);
        metrics.put("krpc.server.idleSeconds",idleSeconds);
        metrics.put("krpc.server.maxPackageSize",maxPackageSize);
        metrics.put("krpc.server.maxConns",maxConns);
        metrics.put("krpc.server.backlog",backlog);
    }
}
