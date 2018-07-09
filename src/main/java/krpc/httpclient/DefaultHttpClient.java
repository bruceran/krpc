package krpc.httpclient;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import krpc.common.InitClose;
import krpc.common.NamedThreadFactory;
import krpc.common.RetCodes;
import krpc.rpc.util.GZip;

@Sharable
public class DefaultHttpClient extends ChannelDuplexHandler implements HttpClient,InitClose {

	static Logger log = LoggerFactory.getLogger(DefaultHttpClient.class);
	
	int maxContentLength = 1000000;
	int workerThreads = 1;
	
	// TODO keepalive, connection pool, https

	NamedThreadFactory workThreadFactory = new NamedThreadFactory("httpclient");
	EventLoopGroup workerGroup;

	Bootstrap bootstrap;
	GZip gzip = new GZip();
	
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
	
	ConcurrentHashMap<String,ReqResInfo> dataMap = new ConcurrentHashMap<>(); // key is sequence
	
    public void init() {

		workerGroup = new NioEventLoopGroup(workerThreads, workThreadFactory);

		bootstrap = new Bootstrap();
		bootstrap.group(workerGroup).channel(NioSocketChannel.class)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ChannelPipeline pipeline = ch.pipeline();
						pipeline.addLast("codec", new HttpClientCodec());
						pipeline.addLast("decompressor", new HttpContentDecompressor());
						pipeline.addLast("aggregator", new HttpObjectAggregator(maxContentLength));
						pipeline.addLast("handler", DefaultHttpClient.this);
					}
				});
		
		bootstrap.option(ChannelOption.TCP_NODELAY, true);
		bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

        log.info("netty http client started");
    }

    public void close() {
    	if( workerGroup == null ) return;
    	
		workerGroup.shutdownGracefully();
		workerGroup = null;
		
        log.info("netty http client stopped");
    }
    
    public HttpClientRes call(HttpClientReq req)  {

    	URL url =  req.getUrlObj();
    	if( url == null ) return new HttpClientRes(RetCodes.HTTPCLIENT_URL_PARSE_ERROR);
    	
        String host = url.getHost();
        int port = url.getPort();
        if( port == -1 ) port = url.getDefaultPort();
        
        try {
	        ChannelFuture future = bootstrap.connect(host,port);
	        Channel channel = future.channel();
	        
	        boolean connected = future.await(req.getTimeout(), TimeUnit.MILLISECONDS);
	        if( !connected ) {
	        	channel.close();
	        	return new HttpClientRes(RetCodes.HTTPCLIENT_TIMEOUT_ERROR);
	        }
	        
			String connId = getConnId(channel);
			ReqResInfo info = new ReqResInfo(req);
			dataMap.put(connId, info);
	        channel.writeAndFlush(req);
	        HttpClientRes res = info.future.get(req.getTimeout(), TimeUnit.MILLISECONDS);
	        
	        dataMap.remove(connId);
	        channel.close();
	        return res;
        
        } catch(InterruptedException e) {
        	return new HttpClientRes(RetCodes.HTTPCLIENT_INTERRUPTED);
        } catch(TimeoutException e) {
        	return new HttpClientRes(RetCodes.HTTPCLIENT_TIMEOUT_ERROR);
        } catch(Exception e) {
        	return new HttpClientRes(RetCodes.HTTPCLIENT_CONNECT_EXCEPTION);
        }
    }

    DefaultHttpRequest convertReq(HttpClientReq data, ChannelHandlerContext ctx) {

    	URL url =  data.getUrlObj();

    	DefaultHttpRequest req = null;
    	
		if( data.getContent() != null && !data.getContent().isEmpty() ) {
			
			ByteBuf bb =  null;
			boolean allowGzip = data.isGzip() && data.getContent().length() >= data.getMinSizeToGzip();
			if( !allowGzip  ) {
	        	bb = ByteBufUtil.writeUtf8(ctx.alloc(), data.getContent());
			} else {
				bb = ctx.alloc().buffer();
				try {
					String charset = stripCharSet(data.getContentType());
					byte[] bytes = data.getContent().getBytes(charset);
					gzip.zip(bytes,bb);
				} catch(Exception e) {
					ReferenceCountUtil.release(bb);
					return null;
				}
			}
			
	    	req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(data.getMethod()), data.getPathQuery(),bb);
			req.headers().set(HttpHeaderNames.CONTENT_TYPE,data.getContentType());
			req.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bb.readableBytes());
			if( allowGzip ) {
				req.headers().set(HttpHeaderNames.CONTENT_ENCODING,"gzip");
			}
		} else {
	    	req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(data.getMethod()), data.getPathQuery());
		}
		req.headers().set(HttpHeaderNames.ACCEPT_ENCODING,"gzip");

		if( data.getHeaders() != null ) {
			for( Map.Entry<String, String> entry: data.getHeaders().entrySet() ) {
				req.headers().set(entry.getKey(),entry.getValue());
			}
		}

		req.headers().set(HttpHeaderNames.HOST, url.getHost());
		req.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		
		return req;
    }

    HttpClientRes convertRes(FullHttpResponse data) {
    	HttpClientRes res = new HttpClientRes(0);
    	res.setHttpCode(data.status().code());
		res.setHeaders(data.headers());
		String contentType = stripContentType( data.headers().get(HttpHeaderNames.CONTENT_TYPE) );
		if( contentType != null )
			res.setContentType(contentType);
		ByteBuf bb = data.content();
		if( bb != null ) {
			String content = bb.toString(Charset.forName("utf-8"));
			res.setContent(content);
		}
		return res;    	
    }

	@Override
	public void channelRead(final ChannelHandlerContext ctx, final Object msg) {

		String connId = getConnId(ctx.channel());
        ReqResInfo info = dataMap.get(connId);

		try {
			FullHttpResponse  httpRes = (FullHttpResponse)msg;
			
	        if (!httpRes.decoderResult().isSuccess()) {
	        	if( info != null ) info.setRes(new HttpClientRes(RetCodes.HTTPCLIENT_RES_PARSE_ERROR));
	            return;
	        }
	
	        HttpClientRes res = convertRes(httpRes);
	        if( info != null ) info.setRes(res);
	        
		} finally {
			ReferenceCountUtil.release(msg);
		}

	}
     
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    	if( msg instanceof HttpClientReq ) {
    		HttpClientReq data = (HttpClientReq)msg;
        	DefaultHttpRequest req = convertReq(data,ctx);
			ctx.writeAndFlush(req, promise);
    	} else {
    		super.write(ctx, msg, promise);
    	}    	
    }

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		//String connId = getConnId(ctx.channel());
		//log.info("http connection started, connId={}", connId);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		//String connId = getConnId(ctx.channel());
		//log.info("http connection ended, connId={}", connId);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {	
		String connId = getConnId(ctx.channel());
		log.error("http connection exception, connId="+connId+",msg="+cause.toString(),cause);
		ctx.close();
	}

	String getConnId(Channel ch) {
		return parseIpPort(ch.remoteAddress().toString()) + ":" + ch.id().asShortText();
	}	
	
	String parseIpPort(String s) {
		int p = s.indexOf("/");

		if (p >= 0)
			return s.substring(p + 1);
		else
			return s;
	}
	
    String stripContentType(String contentType) {
    	if( contentType == null ) return null;
    	int p = contentType.indexOf(";");
    	if( p >= 0 ) return contentType.substring(0,p).trim();
    	return contentType;
    }
    
    String stripCharSet(String contentType) {
    	if( contentType == null ) return null;
    	int p = contentType.indexOf(";");
    	if( p >= 0 ) return contentType.substring(p+1).trim();
    	return "utf-8";
    }

	public int getMaxContentLength() {
		return maxContentLength;
	}

	public void setMaxContentLength(int maxContentLength) {
		this.maxContentLength = maxContentLength;
	}

	public int getWorkerThreads() {
		return workerThreads;
	}

	public void setWorkerThreads(int workerThreads) {
		this.workerThreads = workerThreads;
	}
    	
}

