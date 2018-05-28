package krpc.rpc.web.impl;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import krpc.common.InitClose;
import krpc.rpc.core.RpcData;
import krpc.rpc.core.StartStop;
import krpc.rpc.util.NamedThreadFactory;
import krpc.rpc.web.DefaultWebReq;
import krpc.rpc.web.DefaultWebRes;
import krpc.rpc.web.HttpTransport;
import krpc.rpc.web.HttpTransportCallback;
import krpc.rpc.web.RetCodes;
import krpc.rpc.web.WebConstants;

@Sharable
public class NettyHttpServer extends ChannelDuplexHandler implements HttpTransport,InitClose,StartStop {

	static Logger log = LoggerFactory.getLogger(NettyHttpServer.class);

	int port = 8600;
	String host = "*";
	int idleSeconds = 60;
	int maxContentLength = 1000000;
	int maxConns = 500000;
	int workerThreads = 0;
	int backlog = 300;

	NamedThreadFactory bossThreadFactory = new NamedThreadFactory("web_boss");
	NamedThreadFactory workThreadFactory = new NamedThreadFactory("web_work");

	EventLoopGroup bossGroup;
	EventLoopGroup workerGroup;
	Channel serverChannel;

	ConcurrentHashMap<String, Channel> conns = new ConcurrentHashMap<String, Channel>();

	HttpTransportCallback callback;

	AtomicBoolean stopFlag = new AtomicBoolean();
	
	ServerBootstrap serverBootstrap;
	
	public NettyHttpServer() {}
	
	public NettyHttpServer(int port,HttpTransportCallback callback) {
		this.port = port;
		this.callback = callback;
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
						pipeline.addLast("codec", new HttpServerCodec());
						pipeline.addLast("expectContinue", new HttpServerExpectContinueHandler());						
						// pipeline.addLast("uploader2", new HttpFileUploadAggregator2(uploadDir, maxUploadLength)) // todo
						pipeline.addLast("aggregator", new HttpObjectAggregator(maxContentLength));
			            pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
			            //pipeline.addLast("compressor", new HttpContentCompressor());					
						pipeline.addLast("timeout", new IdleStateHandler(0, 0, idleSeconds));
						pipeline.addLast("handler", NettyHttpServer.this);
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
			addr = new InetSocketAddress(port);
		} else {
			addr = new InetSocketAddress(host, port);
		}
		serverChannel = serverBootstrap.bind(addr).syncUninterruptibly().channel();
		log.info("netty http server started on host(" + host + ") port(" + port + ")");		
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

	Channel getChannel(String connId) {
		return conns.get(connId);
	}

	String getConnId(ChannelHandlerContext ctx) {
		Channel ch = ctx.channel();
		return parseIpPort(ch.remoteAddress().toString()) + ":" + ch.id().asShortText();
	}

	public void stop() {
		stopFlag.set(true);
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
			ctx.close();
			log.error("connection started, connId={}, but max connections exceeded, conn not allowed", connId);
			return;
		}

		log.info("connection started, connId={}", connId);

		conns.put(connId, ctx.channel());
		if( callback != null)
			callback.connected(connId, parseIpPort(ctx.channel().localAddress().toString()));
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		//debugLog("channelInactive");
		String connId = getConnId(ctx);
		conns.remove(connId);
		log.info("connection ended, connId={}", connId);
		if( callback != null)
			callback.disconnected(connId);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		//debugLog("userEventTriggered, evt="+evt);
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
		//log.error("connection exception, connId="+connId+",msg="+cause.toString(),cause);
		log.error("connection exception, connId="+connId+",msg="+cause.toString(),cause);
		ctx.close();
	}
	
	@Override
	public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
		//debugLog("channelRead");
		
		DefaultWebReq req = null;
		try {
			FullHttpRequest  httpReq = (FullHttpRequest)msg;
			
	        if (!httpReq.decoderResult().isSuccess()) {
	            sendError(ctx, HttpResponseStatus.BAD_REQUEST,RetCodes.DECODE_REQ_ERROR);
	            return;
	        }
	
	        /*
	        if (httpReq.method() == HttpMethod.OPTIONS ) {   // todo cors
	            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,RetCodes.HTTP_METHOD_NOT_ALLOWED);
	            return;        	
	        }
	        */
	        
	        if (httpReq.method() != HttpMethod.GET && httpReq.method() != HttpMethod.POST && httpReq.method() != HttpMethod.HEAD
	        		&& httpReq.method() != HttpMethod.PUT && httpReq.method() != HttpMethod.DELETE ) {
	            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,RetCodes.HTTP_METHOD_NOT_ALLOWED);
	            return;
	        }
	
			req = convertReq(httpReq);
		} finally {
			ReferenceCountUtil.release(msg);
		}
		
		String connId = getConnId(ctx);
		
		if( callback != null ) {
			try {
				callback.receive(connId, req);
			} catch (Exception ex) {
				ctx.close();
				log.error("impossible exception, connId=" + connId, ex);
			}			
		}
	}

    DefaultWebReq convertReq(FullHttpRequest data) {
		DefaultWebReq req = new DefaultWebReq();
		
		req.setVersion(data.protocolVersion());
		req.setMethod(data.method());
		req.setKeepAlive(HttpUtil.isKeepAlive(data));
	
		String uri = data.uri();
	
		int p1 = findPathEndIndex(uri);
		String path = p1 >= 0 ? uri.substring(0,p1) : uri;
		int p2 = uri.indexOf('?');
		String queryString = p2 >= 0 ? uri.substring(p2+1) : "";
		req.setPath(path);
		req.setQueryString(queryString);
		
		req.setHeaders(data.headers());
		req.setCookies(decodeCookie(data));
	
		ByteBuf bb = data.content();
		if( bb != null ) {
			String content = bb.toString(Charset.forName(req.getCharSet()));
			req.setContent(content);
		}
		
		return req;
	}

	public boolean send(String connId, DefaultWebRes data) {

		if (data == null)
			return false;

		Channel ch = getChannel(connId);
		if (ch == null) {
			log.error("connection not found, connId={}", connId);
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
    	if( msg instanceof DefaultWebRes ) {
        	DefaultWebRes data = (DefaultWebRes)msg;
        	DefaultFullHttpResponse res = convertRes(data,ctx);
    		if (!data.isKeepAlive()) {
    			ctx.writeAndFlush(res, promise).addListener(ChannelFutureListener.CLOSE);
            } else {
            	res.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            	ctx.writeAndFlush(res, promise);
            }     
    	} else {
    		super.write(ctx, msg, promise);
    	}    	
    }

	DefaultFullHttpResponse convertRes(DefaultWebRes data,ChannelHandlerContext ctx) {
		DefaultFullHttpResponse res = null;

		if( data.getContent() != null && !data.getContent().isEmpty() ) {
			
			int size = ByteBufUtil.utf8MaxBytes(data.getContent());
        	ByteBuf bb = ctx.alloc().buffer(size);
// System.out.println("http bb="+bb.getClass().getName());    // io.netty.buffer.PooledUnsafeDirectByteBuf  

			bb.writeCharSequence(data.getContent(), Charset.forName(data.getCharSet()));
			int len = bb.readableBytes();
			if( data.isHeadMethod()) {
				res = new DefaultFullHttpResponse(data.getVersion(), HttpResponseStatus.valueOf(data.getHttpCode()));
				ReferenceCountUtil.release(bb);
			} else {
				res = new DefaultFullHttpResponse(data.getVersion(), HttpResponseStatus.valueOf(data.getHttpCode()),bb);
			}
			res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, len);
		} else {
			res = new DefaultFullHttpResponse(data.getVersion(), HttpResponseStatus.valueOf(data.getHttpCode()));
		}

		if( data.getHeaders() != null ) {
			for( String key: data.getHeaders().names() ) {
				res.headers().set(key,data.getHeaders().get(key));
			}
		}

		if( data.getCookies() != null ) {
			for( Cookie c: data.getCookies() ) {
				String s = ServerCookieEncoder.STRICT.encode(c);
				res.headers().add(HttpHeaderNames.SET_COOKIE, s);
			}
		}

		res.headers().set(HttpHeaderNames.SERVER, WebConstants.Server);
		
		return res;
	}

    void sendError(ChannelHandlerContext ctx, HttpResponseStatus status,int retCode) {	
    	ByteBuf bb = ctx.alloc().buffer(32);
    	String s = String.format(WebConstants.ContentFormat, retCode , RetCodes.retCodeText(retCode));
    	bb.writeCharSequence(s, CharsetUtil.UTF_8);
    	int len = bb.readableBytes();
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, bb);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, len);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
	HashMap<String,String> decodeCookie(FullHttpRequest data) {
		String cookie = data.headers().get(HttpHeaderNames.COOKIE);
		if (cookie != null && !cookie.isEmpty() ) {
			Set<Cookie> decoded = ServerCookieDecoder.STRICT.decode(cookie);
	        if (decoded != null && decoded.size() > 0 ) {
	    		HashMap<String,String> cookies = new HashMap<String,String>();
	            for(Cookie c: decoded) {
	           		cookies.put(c.name(), c.value());
	            }
	            return cookies;
	        }
	    }
		return null;
	}

	void debugLog(String msg) {
		if( log.isDebugEnabled())
			log.debug(msg);
	}
	
	public String getAddr(String connId) {
		int p = connId.lastIndexOf(":");
		return connId.substring(0, p);
	}
	
	String parseIpPort(String s) {
		int p = s.indexOf("/");

		if (p >= 0)
			return s.substring(p + 1);
		else
			return s;
	}

	byte[] getBytes(String content,String charSet) {
		try {
			return content.getBytes(charSet);
		} catch(Exception e) {
			return null;
		}
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

	public HttpTransportCallback getCallback() {
		return callback;
	}

	public void setCallback(HttpTransportCallback callback) {
		this.callback = callback;
	}

	public int getBacklog() {
		return backlog;
	}

	public void setBacklog(int backlog) {
		this.backlog = backlog;
	}

}
