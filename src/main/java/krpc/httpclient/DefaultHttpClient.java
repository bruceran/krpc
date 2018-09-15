package krpc.httpclient;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import krpc.common.InitClose;
import krpc.common.NamedThreadFactory;
import krpc.common.RetCodes;
import krpc.rpc.util.GZip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.*;

@Sharable
public class DefaultHttpClient extends ChannelDuplexHandler implements HttpClient, InitClose {

    static Logger log = LoggerFactory.getLogger(DefaultHttpClient.class);

    int maxResponseContentLength = 1000000;

    int workerThreads = 1;

    int minSizeToGzip = 2048;
    int keepAliveSeconds = 60;
    int keepAliveRequests = 100;
    int keepAliveConnections = 1; // cached connections

    NamedThreadFactory workThreadFactory = new NamedThreadFactory("krpc_httpclient");
    EventLoopGroup workerGroup;

    GZip gzip;
    SslContext sslCtx;
    Bootstrap bootstrap;
    Bootstrap sslBootstrap;

    ConcurrentHashMap<String, ReqResInfo> dataMap = new ConcurrentHashMap<>(); // key is sequence

    ConcurrentHashMap<String, ArrayBlockingQueue<ChannelInfo>> channelMap = new ConcurrentHashMap<>();

    public void init() {

        gzip = new GZip();

        try {
            sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } catch (Exception e) {
            log.error("ssl context init failed");
        }

        workerGroup = new NioEventLoopGroup(workerThreads, workThreadFactory);

        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("codec", new HttpClientCodec());
                        pipeline.addLast("decompressor", new HttpContentDecompressor());
                        pipeline.addLast("aggregator", new HttpObjectAggregator(maxResponseContentLength));
                        pipeline.addLast("handler", DefaultHttpClient.this);
                    }
                });

        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

        sslBootstrap = new Bootstrap();
        sslBootstrap.group(workerGroup).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("ssl", sslCtx.newHandler(ch.alloc()));
                        pipeline.addLast("codec", new HttpClientCodec());
                        pipeline.addLast("decompressor", new HttpContentDecompressor());
                        pipeline.addLast("aggregator", new HttpObjectAggregator(maxResponseContentLength));
                        pipeline.addLast("handler", DefaultHttpClient.this);
                    }
                });

        sslBootstrap.option(ChannelOption.TCP_NODELAY, true);
        sslBootstrap.option(ChannelOption.SO_KEEPALIVE, true);

        log.info("netty http client started");
    }

    public void close() {
        if (workerGroup == null) return;

        workerGroup.shutdownGracefully();
        workerGroup = null;

        log.info("netty http client stopped");
    }


    public HttpClientRes call(HttpClientReq req) {

        HttpClientRes res = null;
        ConnectResult cr = null;
        String connId = null;

        try {
            cr = getConnection(req);
            if (  cr.retCode != 0) return new HttpClientRes(cr.retCode);

            Channel channel = cr.channelInfo.channel;
            connId = getConnId(channel);
            ReqResInfo info = new ReqResInfo(req);
            dataMap.put(connId, info);
            channel.writeAndFlush(req);
            res = info.future.get(req.getTimeout(), TimeUnit.MILLISECONDS);

        } catch (InterruptedException e) {
            res = new HttpClientRes(RetCodes.HTTPCLIENT_INTERRUPTED);
        } catch (TimeoutException e) {
            res = new HttpClientRes(RetCodes.HTTPCLIENT_TIMEOUT);
        } catch (Exception e) {
            log.error("http client call exception", e);
            res = new HttpClientRes(RetCodes.HTTPCLIENT_CONNECTION_BROKEN);
        }

        if (connId != null)
            dataMap.remove(connId);
        if (cr != null)
            closeConnection(req, res, cr.channelInfo);
        return res;
    }

    String getKey(HttpClientReq req) {
        URL url = req.getUrlObj();
        String schema = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();
        if (port == -1) port = url.getDefaultPort();
        return schema + "//" + host + ":" + port;
    }

    ConnectResult getConnection(HttpClientReq req) throws InterruptedException {
        if (!req.isKeepAlive()) {
            return getConnectionNoCache(req);
        }

        String key = getKey(req);
        ArrayBlockingQueue<ChannelInfo> queue = channelMap.get(key);
        if (queue == null) {

            return getConnectionNoCache(req);
        }
        ChannelInfo channelInfo = queue.poll();
        if (channelInfo == null || !channelInfo.channel.isActive()) {
            return getConnectionNoCache(req);
        }

        if (keepAliveSeconds > 0) {
            if (System.currentTimeMillis() - channelInfo.createTime >= keepAliveSeconds * 1000) {
                channelInfo.channel.close();
                return getConnectionNoCache(req);
            }
        }

        if (keepAliveRequests > 0) {
            if (channelInfo.count >= keepAliveRequests) {
                channelInfo.channel.close();
                return getConnectionNoCache(req);
            }
            channelInfo.count++;
        }

        return new ConnectResult(channelInfo);
    }

    ConnectResult getConnectionNoCache(HttpClientReq req) throws InterruptedException {

        URL url = req.getUrlObj();
        if (url == null) return new ConnectResult(RetCodes.HTTPCLIENT_URL_PARSE_ERROR);

        String schema = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();
        if (port == -1) port = url.getDefaultPort();

        ChannelFuture future = schema.equals("http") ? bootstrap.connect(host, port) : sslBootstrap.connect(host, port);
        Channel channel = future.channel();

        boolean done = future.await(req.getTimeout(), TimeUnit.MILLISECONDS);
        if (!done) {
            channel.close();
            return new ConnectResult(RetCodes.HTTPCLIENT_TIMEOUT);
        }

        if (channel.isActive())
            return new ConnectResult(new ChannelInfo(channel));
        else
            return new ConnectResult(RetCodes.HTTPCLIENT_NO_CONNECT);
    }

    void closeConnection(HttpClientReq req, HttpClientRes res, ChannelInfo channelInfo) {

        if (!req.isKeepAlive() || res.getRetCode() != 0) {
            channelInfo.channel.close();
            return;
        }
        String key = getKey(req);
        ArrayBlockingQueue<ChannelInfo> queue = channelMap.get(key);
        if (queue == null) {
            queue = new ArrayBlockingQueue<ChannelInfo>(keepAliveConnections);
            ArrayBlockingQueue<ChannelInfo> old = channelMap.putIfAbsent(key, queue);
            if (old != null) queue = old;
        }
        boolean ok = queue.offer(channelInfo);
        if (!ok) {
            channelInfo.channel.close();
            return;
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpClientReq) {

            HttpClientReq data = (HttpClientReq) msg;
            DefaultFullHttpRequest req = convertReq(data, ctx);
            ctx.writeAndFlush(req, promise);

        } else {
            super.write(ctx, msg, promise);
        }
    }

    DefaultFullHttpRequest convertReq(HttpClientReq data, ChannelHandlerContext ctx) {

        URL url = data.getUrlObj();

        DefaultFullHttpRequest req = null;

        if (data.getContent() != null && !data.getContent().isEmpty()) {

            ByteBuf bb = null;
            boolean allowGzip = data.isGzip() && data.getContent().length() >= minSizeToGzip;
            if (!allowGzip) {
                bb = ByteBufUtil.writeUtf8(ctx.alloc(), data.getContent());
            } else {
                bb = ctx.alloc().buffer();
                try {
                    String charset = parseCharSet(data.getContentType());
                    byte[] bytes = data.getContent().getBytes(charset);
                    gzip.zip(bytes, bb);
                } catch (Exception e) {
                    ReferenceCountUtil.release(bb);
                    throw new RuntimeException("encode request exception", e);
                }
            }

            req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(data.getMethod()), data.getPathQuery(), bb);
            req.headers().set(HttpHeaderNames.CONTENT_TYPE, data.getContentType());
            req.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bb.readableBytes());
            if (allowGzip) {
                req.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);
            }
        } else {
            req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(data.getMethod()), data.getPathQuery());
        }
        req.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);

        if (data.getHeaders() != null) {
            for (Map.Entry<String, String> entry : data.getHeaders().entrySet()) {
                req.headers().set(entry.getKey(), entry.getValue());
            }
        }

        String host = url.getHost();
        int port = url.getPort();
        if (port != -1) host += ":" + port;
        req.headers().set(HttpHeaderNames.HOST, host);

        HttpUtil.setKeepAlive(req, data.isKeepAlive());

        req.headers().set(HttpHeaderNames.USER_AGENT, "krpc httpclient 1.0");

        return req;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        String connId = getConnId(ctx.channel());
        ReqResInfo info = dataMap.get(connId);

        try {
            FullHttpResponse httpRes = (FullHttpResponse) msg;

            if (!httpRes.decoderResult().isSuccess()) {
                if (info != null) info.setRes(new HttpClientRes(RetCodes.HTTPCLIENT_RES_PARSE_ERROR));
                return;
            }

            HttpClientRes res = convertRes(httpRes);
            if (info != null) info.setRes(res);
        } finally {
            ReferenceCountUtil.release(msg);
        }

    }

    HttpClientRes convertRes(FullHttpResponse data) {
        HttpClientRes res = new HttpClientRes(0);
        res.setHttpCode(data.status().code());
        res.setHeaders(data.headers());
        String contentType = parseContentType(data.headers().get(HttpHeaderNames.CONTENT_TYPE));
        if (contentType != null)
            res.setContentType(contentType);
        ByteBuf bb = data.content();
        if (bb != null) {
            String content = bb.toString(Charset.forName("utf-8"));
            res.setContent(content);
        }
        // System.out.println(HttpUtil.isKeepAlive(data));
        return res;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String connId = getConnId(ctx.channel());
        log.debug("http connection started, connId={}", connId);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String connId = getConnId(ctx.channel());
        log.debug("http connection ended, connId={}", connId);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String connId = getConnId(ctx.channel());
        log.error("http connection exception, connId=" + connId + ",msg=" + cause.toString(), cause);
        ctx.close();
    }

    String getConnId(Channel ch) {
        SocketAddress addr = ch.remoteAddress();
        return parseIpPort(addr.toString()) + ":" + ch.id().asShortText();
    }

    String parseIpPort(String s) {
        int p = s.indexOf("/");

        if (p >= 0)
            return s.substring(p + 1);
        else
            return s;
    }

    String parseContentType(String contentType) {
        if (contentType == null) return null;
        int p = contentType.indexOf(";");
        if (p >= 0) return contentType.substring(0, p).trim();
        return contentType;
    }

    String parseCharSet(String contentType) {
        if (contentType == null) return null;
        int p = contentType.indexOf(";");
        if (p >= 0) return contentType.substring(p + 1).trim();
        return "utf-8";
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getKeepAliveConnections() {
        return keepAliveConnections;
    }

    public void setKeepAliveConnections(int keepAliveConnections) {
        this.keepAliveConnections = keepAliveConnections;
    }

    public int getKeepAliveRequests() {
        return keepAliveRequests;
    }

    public void setKeepAliveRequests(int keepAliveRequests) {
        this.keepAliveRequests = keepAliveRequests;
    }

    public int getMaxResponseContentLength() {
        return maxResponseContentLength;
    }

    public void setMaxResponseContentLength(int maxResponseContentLength) {
        this.maxResponseContentLength = maxResponseContentLength;
    }

    public int getMinSizeToGzip() {
        return minSizeToGzip;
    }

    public void setMinSizeToGzip(int minSizeToGzip) {
        this.minSizeToGzip = minSizeToGzip;
    }


    static class ReqResInfo {
        HttpClientReq req;
        HttpClientRes res;
        CompletableFuture<HttpClientRes> future;

        ReqResInfo(HttpClientReq req) {
            this.req = req;
            this.future = new CompletableFuture<HttpClientRes>();
        }

        void setRes(HttpClientRes res) {
            this.res = res;
            future.complete(res);
        }
    }

    static class ChannelInfo {
        Channel channel = null;
        long createTime = System.currentTimeMillis();
        int count = 1;

        ChannelInfo(Channel channel) {
            this.channel = channel;
        }
    }

    static class ConnectResult {
        int retCode = 0;
        ChannelInfo channelInfo = null;

        ConnectResult(int retCode) {
            this.retCode = retCode;
        }

        ConnectResult(ChannelInfo channelInfo) {
            this.channelInfo = channelInfo;
        }
    }

}

