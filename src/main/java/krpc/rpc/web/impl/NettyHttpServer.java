package krpc.rpc.web.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;

import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import krpc.common.InitClose;
import krpc.common.NamedThreadFactory;
import krpc.rpc.web.DefaultWebReq;
import krpc.rpc.web.DefaultWebRes;
import krpc.rpc.web.HttpTransport;
import krpc.rpc.web.HttpTransportCallback;
import krpc.common.RetCodes;
import krpc.common.StartStop;
import krpc.rpc.web.WebConstants;
import krpc.rpc.web.WebUtils;

@Sharable
public class NettyHttpServer extends ChannelDuplexHandler implements HttpTransport,InitClose,StartStop {

	static Logger log = LoggerFactory.getLogger(NettyHttpServer.class);

	int port = 8600;
	String host = "*";
	int idleSeconds = 30;
	int maxConns = 500000;
	int workerThreads = 0;
	int backlog = 128;

	int maxInitialLineLength = 4096;
	int maxHeaderSize = 8192;
	int maxChunkSize = 8192;
	int maxContentLength = 1000000;
	
	String dataDir = ".";
	long maxUploadLength = 5000000;

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

		String uploadDir = dataDir + "/upload";
		new File(uploadDir).mkdirs();
		
		bossGroup = new NioEventLoopGroup(1, bossThreadFactory);
		workerGroup = new NioEventLoopGroup(workerThreads, workThreadFactory);

		serverBootstrap = new ServerBootstrap();
		serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ChannelPipeline pipeline = ch.pipeline();
						pipeline.addLast("timeout", new IdleStateHandler(0, 0, idleSeconds));
						pipeline.addLast("codec", new HttpServerCodec(maxInitialLineLength,maxHeaderSize,maxChunkSize));	
						pipeline.addLast("decompressor", new HttpContentDecompressor());
						pipeline.addLast("upload", new NettyHttpUploadHandler(uploadDir, maxUploadLength));
						pipeline.addLast("aggregator", new HttpObjectAggregator(maxContentLength));
			            //pipeline.addLast("chunkedWriter", new ChunkedWriteHandler()); // use ZeroCopy FileRegion instead
			            pipeline.addLast("compressor", new HttpContentCompressor());
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

	        if (httpReq.method() != HttpMethod.GET && httpReq.method() != HttpMethod.POST 
	        		&& httpReq.method() != HttpMethod.PUT && httpReq.method() != HttpMethod.DELETE 
	        		&& httpReq.method() != HttpMethod.HEAD  && httpReq.method() != HttpMethod.OPTIONS 
	        		) {
	            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,RetCodes.HTTP_METHOD_NOT_ALLOWED);
	            return;
	        }
        	
        	if( httpReq.method() == HttpMethod.OPTIONS  ) {
	        	String origin = httpReq.headers().get(HttpHeaderNames.ORIGIN);
	        	String requestMethod = httpReq.headers().get(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD);
	        	if( origin == null || requestMethod == null ) {
	        		sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,RetCodes.HTTP_METHOD_NOT_ALLOWED);
		            return;
	        	}
        	}
        	
			req = convertReq(httpReq);
			
			String connId = getConnId(ctx);
			
			if( callback != null ) {
				try {
					callback.receive(connId, req);
				} catch (Exception ex) {
					ctx.close();
					log.error("impossible exception, connId=" + connId, ex);
				}			
			}
		} finally {
			ReferenceCountUtil.release(msg);
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
		path = WebUtils.decodeUrl(path);
		int p2 = uri.indexOf('?');
		String queryString = p2 >= 0 ? uri.substring(p2+1) : "";
		req.setPath(path);
		req.setQueryString(queryString);
		
		req.setHeaders(data.headers());
	
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
			
			String downloadFileStr = data.getStringResult(WebConstants.DOWNLOAD_FILE_FIELD);
			
			if( downloadFileStr != null ) {
				writeStaticFile(ch,data,downloadFileStr);
			} else {
				ch.writeAndFlush(data);
			}
			return true;
		}
		
		return false;
	}

	void writeStaticFile(Channel ch,DefaultWebRes data,String downloadFileStr) {
		try {
			writeStaticFile0(ch,data,downloadFileStr);
		} catch(Exception e) {
			DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
	        ch.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);			
		}
	}
	
    void writeStaticFile0(Channel ch, DefaultWebRes data,String downloadFileStr) throws IOException {
		
		File downloadFile = new File(downloadFileStr);
		
		if( ch.pipeline().get("compressor") != null ) {
			ch.pipeline().remove("compressor"); // file region cannot be used with compressor
		}
		
		DefaultHttpResponse res = new DefaultHttpResponse(data.getVersion(), HttpResponseStatus.valueOf(data.getHttpCode()));

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
    	res.headers().set(HttpHeaderNames.DATE, WebUtils.formatDate(new GregorianCalendar().getTime()));
		res.headers().set(HttpHeaderNames.CONTENT_TYPE, WebUtils.getContentType(downloadFile.getName()));
		res.headers().set(HttpHeaderNames.LAST_MODIFIED, WebUtils.formatDate(new Date(downloadFile.lastModified())));
    	res.headers().set(HttpHeaderNames.ETAG,WebUtils.generateEtag(downloadFile));
		setCacheControl(data,res);
		setAttachment(data,res,downloadFile.getName());
		
		if ( data.isKeepAlive() ) {
			res.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
		} 

		DefaultFileRegion fileRegion = null;
		
		switch(data.getHttpCode()) {
			case 304:
				break;
			case 206: 
				{
					long len = downloadFile.length();
					
					String fileRange = (String)data.getResults().get(WebConstants.DOWNLOAD_FILE_RANGE_FIELD);
					res.headers().set(HttpHeaderNames.CONTENT_RANGE,  "bytes "+fileRange+"/"+len );
					
					String[] ss = fileRange.split("-");
					long min = Long.parseLong(ss[0]);
					long max = Long.parseLong(ss[1]);
					len = (max - min + 1);
					
					if( !data.isHeadMethod() ) {
						fileRegion = new DefaultFileRegion(new RandomAccessFile(downloadFile,"r").getChannel(), min, len);
					}
					res.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));
				}
				break;
			default:
				{
					long len = downloadFile.length();
					
					if( !data.isHeadMethod() ) {
						fileRegion = new DefaultFileRegion(new RandomAccessFile(downloadFile,"r").getChannel(), 0, len);
					}			
					
					// res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
					res.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));
				}
				break;
		}

		ch.write(res);
		if( fileRegion != null ) {
			ch.write(fileRegion);
		}
		ChannelFuture future = ch.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		
		if (!data.isKeepAlive()) {
			future.addListener(ChannelFutureListener.CLOSE);
        }  
		
		boolean needAutoDelete = needAutoDelete(data);
		if( needAutoDelete ) {
			ChannelFutureListener deleteListener = new ChannelFutureListener() {
		        public void operationComplete(ChannelFuture future) {
		        	downloadFile.delete();
		        }
		    };
			future.addListener( deleteListener );
		}
	}
	
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    	if( msg instanceof DefaultWebRes ) {
        	DefaultWebRes data = (DefaultWebRes)msg;
        	ByteString downloadStream = data.getByteStringResult(WebConstants.DOWNLOAD_STREAM_FIELD);
        	if( downloadStream != null ) {
				writeStream(ctx,promise,data,downloadStream);
			} else {
				writeDynamicContent(ctx, promise, data);     
			}
    	} else {
    		super.write(ctx, msg, promise);
    	}    	
    }
    
	void writeStream(ChannelHandlerContext ctx,ChannelPromise promise, DefaultWebRes data,ByteString downloadStream) {
		try {
			writeStream0(ctx,promise,data,downloadStream);
		} catch(Exception e) {
			DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
			ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);			
		}
	}
	
    void writeStream0(ChannelHandlerContext ctx, ChannelPromise promise, DefaultWebRes data,ByteString downloadStream) throws IOException {
		
		int size = downloadStream.size();
    	ByteBuf bb = ctx.alloc().buffer(size);  //  ByteBuf type: io.netty.buffer.PooledUnsafeDirectByteBuf  
    	bb.writeBytes(downloadStream.asReadOnlyByteBuffer());
    	
    	DefaultFullHttpResponse res = null;
    	
    	int len = bb.readableBytes();
		if( data.isHeadMethod()) {
			res = new DefaultFullHttpResponse(data.getVersion(), HttpResponseStatus.valueOf(data.getHttpCode()));
			ReferenceCountUtil.release(bb);
		} else {
			res = new DefaultFullHttpResponse(data.getVersion(), HttpResponseStatus.valueOf(data.getHttpCode()),bb);
		}
		res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, len);

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
		
		String filename = data.getStringResult("filename");
		
		res.headers().set(HttpHeaderNames.SERVER, WebConstants.Server);
    	res.headers().set(HttpHeaderNames.DATE, WebUtils.formatDate(new GregorianCalendar().getTime()));
		res.headers().set(HttpHeaderNames.CONTENT_TYPE, WebUtils.getContentType(filename));
		res.headers().set(HttpHeaderNames.LAST_MODIFIED, WebUtils.formatDate(new Date()));
		res.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
		setAttachment(data,res,filename);
		
		if ( data.isKeepAlive() ) {
			res.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
		} 

		ChannelFuture future = ctx.writeAndFlush(res, promise);
		if (!data.isKeepAlive()) {
			future.addListener(ChannelFutureListener.CLOSE);
		} 
	}

	private void writeDynamicContent(ChannelHandlerContext ctx, ChannelPromise promise, DefaultWebRes data) {
		
		DefaultFullHttpResponse res = null;

		if( data.getContent() != null && !data.getContent().isEmpty() ) {
			
			int size = ByteBufUtil.utf8MaxBytes(data.getContent());
        	ByteBuf bb = ctx.alloc().buffer(size);  //  ByteBuf type: io.netty.buffer.PooledUnsafeDirectByteBuf  

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
		res.headers().set(HttpHeaderNames.SERVER, WebConstants.Server);
		
		if (data.isKeepAlive()) {
			res.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
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
		
		ChannelFuture future = ctx.writeAndFlush(res, promise);
		if (!data.isKeepAlive()) {
			future.addListener(ChannelFutureListener.CLOSE);
		} 
	}

    void sendError(ChannelHandlerContext ctx, HttpResponseStatus status,int retCode) {	
    	ByteBuf bb = ctx.alloc().buffer(32);
    	String s = String.format(WebConstants.ContentFormat, retCode , RetCodes.retCodeText(retCode));
    	bb.writeCharSequence(s, CharsetUtil.UTF_8);
    	int len = bb.readableBytes();
    	DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, bb);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, len);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
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

	void setCacheControl(DefaultWebRes data,DefaultHttpResponse res) {
		String expires = data.getStringResult(WebConstants.DOWNLOAD_EXPIRES_FIELD);
		if( expires == null ) return;
		
		GregorianCalendar time = new GregorianCalendar();
	    time.add(Calendar.SECOND, Integer.parseInt(expires));
	    
	    res.headers().set(HttpHeaderNames.EXPIRES, WebUtils.formatDate(time.getTime()));
	    
		if( expires.equals("0") || expires.equals("-1") )
			res.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
		else
			res.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age="+expires);
	}

	void setAttachment(DefaultWebRes data,DefaultHttpResponse res,String filename) {
		String attachment = data.getStringResult("attachment");
		if( attachment == null || attachment.isEmpty() ) return;
		if( isTrue(attachment) ) {
	    	String name = WebUtils.encodeUrl(filename);
	    	String v = String.format("attachment; filename=\"%s\"",name);
	    	res.headers().set(HttpHeaderNames.CONTENT_DISPOSITION,v);
		}
	}

	boolean needAutoDelete(DefaultWebRes data) {
		String autoDelete = data.getStringResult("autoDelete");
		if( autoDelete == null || autoDelete.isEmpty() ) return false;
		return isTrue(autoDelete);
	}

	boolean isTrue(String s) {
		s = s.toLowerCase();
		return s.equals("1") || s.equals("y") || s.equals("t") || s.equals("yes") || s.equals("true");
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

	public String getDataDir() {
		return dataDir;
	}

	public void setDataDir(String dataDir) {
		this.dataDir = dataDir;
	}

	public long getMaxUploadLength() {
		return maxUploadLength;
	}

	public void setMaxUploadLength(long maxUploadLength) {
		this.maxUploadLength = maxUploadLength;
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

}
