package krpc.rpc.impl.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.GlobalEventExecutor;
import krpc.common.InitClose;
import krpc.common.NamedThreadFactory;
import krpc.common.StartStop;
import krpc.rpc.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Sharable
public class NettyClient extends TransportBase implements Transport, TransportChannel, InitClose, StartStop {

    static Logger log = LoggerFactory.getLogger(NettyClient.class);

    int pingSeconds = 60;
    int maxPackageSize = 1000000;
    int connectTimeout = 15000;
    int reconnectSeconds = 1;
    int workerThreads = 0;

    NamedThreadFactory workThreadFactory = new NamedThreadFactory("cli_work");
    NamedThreadFactory timerThreadFactory = new NamedThreadFactory("cli_timer");

    EventLoopGroup workerGroup;
    HashedWheelTimer timer;

    Bootstrap bootstrap;

    Object dummyChannel = new Object();
    ConcurrentHashMap<String, Object> conns = new ConcurrentHashMap<>(); // value cannot be null, so use Object type but Channel type
    ConcurrentHashMap<String, String> connIdMap = new ConcurrentHashMap<>(); // channel.id() -> outside connId

    public NettyClient() {
    }

    public NettyClient(TransportCallback callback, RpcCodec codec, ServiceMetas serviceMetas) {
        this.callback = callback;
        this.codec = codec;
        this.serviceMetas = serviceMetas;
    }

    public void init() {

        workerGroup = new NioEventLoopGroup(workerThreads, workThreadFactory);
        timer = new HashedWheelTimer(timerThreadFactory, 1, TimeUnit.SECONDS);

        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("frame-decoder", new LengthFieldBasedFrameDecoder(maxPackageSize, 4, 4, 0, 0));
                pipeline.addLast("timeout", new IdleStateHandler(0, 0, pingSeconds));
                pipeline.addLast("handler", NettyClient.this);
            }
        });
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_REUSEADDR, true);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        // bootstrap.option(ChannelOption.SO_RCVBUF, 65536);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);

        log.info("netty client started");
    }

    public void start() {
    }

    public void close() {

        if (workerGroup != null) {

            log.info("stopping netty client");

            timer.stop();
            timer = null;

            ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            for (Object ch : conns.values()) {
                if (ch != null && ch != dummyChannel)
                    allChannels.add((Channel) ch);
            }
            ChannelGroupFuture future = allChannels.close();
            future.awaitUninterruptibly();

            workerGroup.shutdownGracefully();
            workerGroup = null;

            log.info("netty client stopped");
        }
    }

    boolean isServerSide() {
        return false;
    }

    Channel getChannel(String connId) {
        Object o = conns.get(connId);
        if (o == dummyChannel)
            return null;
        return (Channel) o;
    }

    public String getConnId(ChannelHandlerContext ctx) {
        Channel ch = ctx.channel();
        return connIdMap.get(ch.id().asLongText());
    }

    public void connect(String connId, String addr) {
        conns.put(connId, dummyChannel);
        reconnect(connId, addr);
    }

    public void disconnect(String connId) {
        Channel ch = getChannel(connId);
        if (ch == null)
            return;
        conns.remove(connId);
        ch.close();
    }

    void reconnect(final String connId, final String addr) {

        if (!conns.containsKey(connId)) {
            return;
        }

        String[] ss = addr.split(":");
        String host = ss[0];
        int port = Integer.parseInt(ss[1]);

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                NettyClient.this.onConnectCompleted(future, connId, addr);
            }
        });
    }

    void scheduleToReconnect(final String connId, final String addr) {

        if (timer != null) { // maybe stopping
            timer.newTimeout(new TimerTask() {

                public void run(Timeout timeout) {
                    reconnect(connId, addr);
                }

            }, reconnectSeconds, TimeUnit.SECONDS);
        }
    }

    void onConnectCompleted(ChannelFuture f, String connId, String addr) {

        if (!f.isSuccess()) {
            log.error("connect failed, connId=" + connId + ", e=" + f.cause().getMessage());
            scheduleToReconnect(connId, addr);
            return;
        }

        Channel ch = f.channel();
        connIdMap.put(ch.id().asLongText(), connId);
        conns.put(connId, ch);
        log.info("connection started, connId={}", connId);
        if (callback != null)
            callback.connected(connId, parseIpPort(ch.localAddress().toString()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //debugLog("channelInactive called");
        String connId = getConnId(ctx);
        connIdMap.remove(ctx.channel().id().asLongText());
        if (connId == null)
            return;
        conns.put(connId, dummyChannel);
        log.info("connection ended, connId={}", connId);
        if (callback != null)
            callback.disconnected(connId);

        String addr = getAddr(connId);
        scheduleToReconnect(connId, addr);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        //debugLog("userEventTriggered called, evt="+evt);
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.ALL_IDLE) {
                Channel ch = ctx.channel();
                ByteBuf encoded = ctx.alloc().buffer(32);
                codec.getReqHeartBeat(encoded);
                ch.writeAndFlush(encoded);
            }
        }
    }

    public int getPingSeconds() {
        return pingSeconds;
    }

    public void setPingSeconds(int pingSeconds) {
        this.pingSeconds = pingSeconds;
    }

    public int getMaxPackageSize() {
        return maxPackageSize;
    }

    public void setMaxPackageSize(int maxPackageSize) {
        this.maxPackageSize = maxPackageSize;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReconnectSeconds() {
        return reconnectSeconds;
    }

    public void setReconnectSeconds(int reconnectSeconds) {
        this.reconnectSeconds = reconnectSeconds;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

}
