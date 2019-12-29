package krpc.trace.adapter;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import krpc.common.InitClose;
import krpc.common.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

@Sharable
public class CatNettyClient extends ChannelDuplexHandler implements InitClose {

    static Logger log = LoggerFactory.getLogger(CatNettyClient.class);

    int connectTimeout = 3000;
    int reconnectSeconds = 1;
    int workerThreads = 1;

    NamedThreadFactory workThreadFactory = new NamedThreadFactory("krpc_cat_worker");

    EventLoopGroup workerGroup;
    Timer timer;

    Bootstrap bootstrap;

    Object dummyChannel = new Object();
    ConcurrentHashMap<String, Object> conns = new ConcurrentHashMap<>(); // value cannot be null, so use Object type but Channel type
    ConcurrentHashMap<String, String> addrMap = new ConcurrentHashMap<>(); // channel.id() -> outside addr

    public void init() {

        timer = new Timer("krpc_cat_netty_timer");

        workerGroup = new NioEventLoopGroup(workerThreads, workThreadFactory);

        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("handler", CatNettyClient.this);
            }
        });
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_REUSEADDR, true);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        // bootstrap.option(ChannelOption.SO_RCVBUF, 65536);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);

        log.info("cat netty client started");
    }


    public void close() {

        if (workerGroup != null) {

            log.info("cat stopping netty client");

            timer.cancel();
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

            log.info("cat netty client stopped");
        }
    }

    boolean isAlive(String addr) {
        return getChannel(addr) != null;
    }

    Channel getChannel(String addr) {
        Object o = conns.get(addr);
        if (o == dummyChannel)
            return null;
        return (Channel) o;
    }

    public String getAddr(ChannelHandlerContext ctx) {
        Channel ch = ctx.channel();
        return addrMap.get(ch.id().asLongText());
    }

    public void connect(String addr) {
        conns.put(addr, dummyChannel);
        reconnect(addr);
    }

    public void disconnect(String addr) {
        Channel ch = getChannel(addr);
        if (ch == null)
            return;
        conns.remove(addr);
        ch.close();
    }

    void reconnect(final String addr) {
//System.out.println("reconnect conns="+conns.keySet());
        if (!conns.containsKey(addr)) {
            return;
        }

        String[] ss = addr.split(":");
        String host = ss[0];
        int port = Integer.parseInt(ss[1]);

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                CatNettyClient.this.onConnectCompleted(future, addr);
            }
        });
    }

    void scheduleToReconnect(final String addr) {

        if (timer != null) { // maybe stopping
            timer.schedule(new TimerTask() {

                public void run() {
                    try {
                        reconnect(addr);
                    } catch(Throwable e) {
                        log.error("reconnect exception",e);
                    }
                }

            }, reconnectSeconds * 1000);
        }
    }

    void onConnectCompleted(ChannelFuture f, String addr) {

        if (!f.isSuccess()) {
            log.error("connect failed, addr=" + addr + ", e=" + f.cause().getMessage());
            scheduleToReconnect(addr);
            return;
        }

        Channel ch = f.channel();
        addrMap.put(ch.id().asLongText(), addr);
        conns.put(addr, ch);
        log.info("connection started, addr={}", addr);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //debugLog("channelInactive called");
        String addr = getAddr(ctx);
        addrMap.remove(ctx.channel().id().asLongText());
        if (addr == null)
            return;

        if( conns.containsKey(addr) ) {
            conns.put(addr, dummyChannel);
        }
        log.info("connection ended, addr={}", addr);

        scheduleToReconnect(addr);
    }

    public boolean send(String addr, StringBuilder data) {

        Channel ch = getChannel(addr);
        if (ch == null) {
            log.error("connection not found, addr={}", addr);
            return false;
        }

        if (ch.isActive()) {
            ch.writeAndFlush(data);
            return true;
        }

        return false;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof StringBuilder) {
            StringBuilder s = (StringBuilder) msg;
            ByteBuf len = ctx.alloc().buffer(4);
            ByteBuf data = ByteBufUtil.writeUtf8(ctx.alloc(), s);
            len.writeInt(data.readableBytes());
            ctx.write(len);
            ctx.writeAndFlush(data, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        // System.out.println("channelRead, msg=" + msg);

        String addr = getAddr(ctx);
        if (addr == null) {
            log.error("channelRead, addr is null"); // donot send to monitor service
            return;
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String addr = getAddr(ctx);
        log.error("connection exception, addr=" + addr + ",msg=" + cause.toString());
        ctx.close();
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
