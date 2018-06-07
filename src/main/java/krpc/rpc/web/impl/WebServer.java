package krpc.rpc.web.impl;

import static krpc.rpc.web.WebConstants.*;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import io.netty.handler.codec.http.QueryStringDecoder;
import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.common.Json;
import krpc.rpc.core.Continue;
import krpc.rpc.core.ErrorMsgConverter;
import krpc.rpc.core.ExecutorManager;
import krpc.rpc.core.FlowControl;
import krpc.rpc.core.RpcCallable;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.core.ServerContext;
import krpc.rpc.core.ServiceMetas;
import krpc.rpc.core.StartStop;
import krpc.rpc.core.proto.RpcMeta;
import krpc.rpc.util.TypeSafe;
import krpc.rpc.util.TypeSafeMap;
import krpc.rpc.web.AsyncPostParsePlugin;
import krpc.rpc.web.AsyncPostSessionPlugin;
import krpc.rpc.web.DefaultWebReq;
import krpc.rpc.web.DefaultWebRes;
import krpc.rpc.web.HttpTransport;
import krpc.rpc.web.HttpTransportCallback;
import krpc.rpc.web.ParserPlugin;
import krpc.rpc.web.PostParsePlugin;
import krpc.rpc.web.PostRenderPlugin;
import krpc.rpc.web.PostSessionPlugin;
import krpc.rpc.web.PreParsePlugin;
import krpc.rpc.web.PreRenderPlugin;
import krpc.rpc.web.RenderPlugin;
import krpc.common.RetCodes;
import krpc.rpc.web.Route;
import krpc.rpc.web.RouteService;
import krpc.rpc.web.RpcDataConverter;
import krpc.rpc.web.SessionService;
import krpc.rpc.web.WebClosure;
import krpc.rpc.web.WebContextData;
import krpc.rpc.web.WebMonitorService;
import krpc.rpc.web.WebPlugin;
import krpc.trace.TraceContext;
import krpc.trace.Span;
import krpc.trace.Trace;

public class WebServer implements HttpTransportCallback, InitClose, StartStop {

	static Logger log = LoggerFactory.getLogger(WebServer.class);

	String sessionIdCookieName = DefaultSessionIdCookieName;
	String sessionIdCookiePath = "";
	int sampleRate = 1;

	FlowControl flowControl;
	ServiceMetas serviceMetas;
	ErrorMsgConverter errorMsgConverter;

	RouteService routeService;
	HttpTransport httpTransport;
	SessionService sessionService;
	RpcDataConverter rpcDataConverter;
	ExecutorManager executorManager;
	WebMonitorService monitorService;

	AtomicInteger seq = new AtomicInteger(0);
	ConcurrentHashMap<String, String> clientConns = new ConcurrentHashMap<String, String>();

	ArrayList<Object> resources = new ArrayList<Object>();

	public void init() {

		resources.add(routeService);
		resources.add(httpTransport);
		resources.add(sessionService);
		resources.add(rpcDataConverter);
		resources.add(executorManager);

		InitCloseUtils.init(resources);
	}

	public void close() {
		InitCloseUtils.close(resources);
	}

	public void start() {
		InitCloseUtils.start(resources);
	}

	public void stop() {
		InitCloseUtils.stop(resources);
	}

	private WebContextData generateCtx(String connId, DefaultWebReq req, Route r) {
		int sequence = nextSequence();
		RpcMeta.Builder builder = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST)
				.setServiceId(r.getServiceId()).setMsgId(r.getMsgId()).setSequence(sequence);

		String traceId = Trace.getAdapter().newTraceId();
		
		boolean needSample = ( traceId.hashCode() % sampleRate ) == 0;
		int sampled = needSample?0:2; // todo 1
		
		String action = serviceMetas.getName(r.getServiceId(), r.getMsgId());
		Trace.startServer(traceId,"","","",sampled,"HTTPSERVER",action);
		TraceContext tctx = Trace.currentContext();
		Trace.setRemoteAddr(getRemoteAddr(connId));
		Span span = tctx.currentSpan();
		
		builder.setTraceId(traceId).setRpcId(span.getRpcId()).setSampled(sampled);

		String peers = "";
		String xff = req.getXff();
		if (!isEmpty(xff)) {
			String remoteIp = getRemoteIp(connId);
			if (!remoteIp.equals(xff)) {
				peers = xff + ":0," + getRemoteAddr(connId);
			} else {
				peers = getRemoteAddr(connId);
			}
		} else {
			peers = getRemoteAddr(connId);
		}

		builder.setPeers(peers);
		builder.setApps("client");
		
		String clientTraceId = getClientTraceId(req);
		if( !isEmpty(clientTraceId) ) {
			String attachement = "ct="+clientTraceId;
			builder.setAttachment(attachement);
		}
		
		RpcMeta meta = builder.build();
		WebContextData ctx = new WebContextData(connId, meta, r, tctx);
		return ctx;
	}
	
	public void receive(String connId, DefaultWebReq req) {

		// route
		Route r = routeService.findRoute(req.getHostNoPort(), req.getPath(), req.getMethod().toString());
		if (r == null) {
			// todo route static file
			/*
			if (req.getMethod() == HttpMethod.GET || req.getMethod() == HttpMethod.HEAD) {
				String sr = routeService.findStaticFile(req.getHost(), req.getPath());
				if (sr != null) {
					routeStaticFile(connId, req, sr);

					return;
				}
			}
			*/

			DefaultWebRes res = generateError(req, RetCodes.HTTP_NOT_FOUND, 404);
			httpTransport.send(connId, res);

			return;
		}

		WebContextData ctx = generateCtx(connId, req, r);
		ServerContext.set(ctx);
		
		// flow control
		if (flowControl != null) {
			if (!flowControl.isAsync()) {
				boolean exceeded = flowControl.exceedLimit(r.getServiceId(), r.getMsgId(),null);
				if (exceeded) {
					sendErrorResponse(ctx, req, RetCodes.FLOW_LIMIT);
					return;
				}
			} else {
				flowControl.exceedLimit(r.getServiceId(), r.getMsgId(), new Continue<Boolean>() {
					public void readyToContinue(Boolean exceeded) {
						
						ServerContext.set(ctx);
						
						if (exceeded) {
							sendErrorResponse(ctx, req, RetCodes.FLOW_LIMIT);
							return;
						}
						continue1(ctx, req);
					}
				});
			}
		}

		continue1(ctx, req);
	}

	void continue1(WebContextData ctx, DefaultWebReq req ) {

		Route r = ctx.getRoute();
		WebPlugin[] plugins = r.getPlugins();

		// preparse
		if (plugins != null) {
			for (WebPlugin p : plugins) {
				if (p instanceof PreParsePlugin) {
					int retCode = ((PreParsePlugin) p).preParse(r.getServiceId(), r.getMsgId(), req);
					if (retCode != 0) {
						sendErrorResponse(ctx, req, retCode);
						return;
					}
				}
			}
		}

		if (r.getVariables() != null) {
			req.getParameters().putAll(r.getVariables());
		}

		// parse queryString, both in url or content
		parseQueryString(req);

		// parse
		boolean hasParser = false;
		if (plugins != null) {
			for (WebPlugin p : plugins) {
				if (p instanceof ParserPlugin) {
					int retCode = ((ParserPlugin) p).parse(r.getServiceId(), r.getMsgId(), req);
					if (retCode != 0) {
						sendErrorResponse(ctx, req, retCode);
						return;
					}
					hasParser = true;
				}
			}
		}

		// standard json parser
		if (!hasParser) {
			parseJsonContent(req);
		}

		// postparse
		if (plugins != null) {
			for (WebPlugin p : plugins) {
				if (p instanceof PostParsePlugin) {
					int retCode = ((PostParsePlugin) p).postParse(r.getServiceId(), r.getMsgId(), req);
					if (retCode != 0) {
						sendErrorResponse(ctx, req, retCode);
						return;
					}
				}
			}
		}

		// asyncpostparse
		if (plugins != null) {
			ArrayList<AsyncPostParsePlugin> list = null;
			for (WebPlugin p : plugins) {
				if (p instanceof AsyncPostParsePlugin) {
					if (list == null)
						list = new ArrayList<AsyncPostParsePlugin>();
					list.add((AsyncPostParsePlugin) p);
				}
			}
			if (list != null) {
				if (list.size() == 1) {

					list.get(0).asyncPostParse(r.getServiceId(), r.getMsgId(), req, new Continue<Integer>() {
						public void readyToContinue(Integer retCode) {
							
							ServerContext.set(ctx);
							
							if (retCode != 0) {
								sendErrorResponse(ctx, req, retCode);
								return;
							}
							continue2(ctx, req);
						}
					});

				} else {
					doMultiAsyncPostParse(ctx, req, list, 0);
				}
				return;
			}
		}

		continue2(ctx, req);
	}

	void doMultiAsyncPostParse(WebContextData ctx, DefaultWebReq req, ArrayList<AsyncPostParsePlugin> list,
			int index) {
		
		Route r = ctx.getRoute();
		
		if (index >= list.size()) {
			continue2(ctx, req);
			return;
		}

		list.get(index).asyncPostParse(r.getServiceId(), r.getMsgId(), req, new Continue<Integer>() {
			public void readyToContinue(Integer retCode) {
				
				ServerContext.set(ctx);
				
				if (retCode != 0) {
					sendErrorResponse(ctx, req, retCode);
					return;
				}
				doMultiAsyncPostParse(ctx, req, list, index + 1);
			}
		});
	}

	void continue2(WebContextData ctx, DefaultWebReq req) {

		Route r = ctx.getRoute();

		if (r.getSessionMode() == Route.SESSION_MODE_ID) {
			String sessionId = getOrNewSessionId(req);
			ctx.setSessionId(sessionId);
		}

		if (r.getSessionMode() == Route.SESSION_MODE_YES || r.getSessionMode() == Route.SESSION_MODE_OPTIONAL) {

			if (sessionService == null) {
				sendErrorResponse(ctx, req, RetCodes.HTTP_NO_SESSIONSERVICE);
				return;
			}

			if (!hasSessionId(req) && r.getSessionMode() == Route.SESSION_MODE_YES) {
				sendErrorResponse(ctx, req, RetCodes.HTTP_NO_LOGIN);
				return;
			}

			String sessionId = getOrNewSessionId(req);
			ctx.setSessionId(sessionId);

			HashMap<String, String> session = new HashMap<String, String>();
			ctx.setSession(session);

			if (hasSessionId(req)) {
				sessionService.load(sessionId, session, new Continue<Integer>() {
					public void readyToContinue(Integer retCode) {
						
						ServerContext.set(ctx);
						
						if (retCode != 0) {
							sendErrorResponse(ctx, req, retCode);
							return;
						}

						if (r.getSessionMode() == Route.SESSION_MODE_YES) {
							String flag = session.get(LoginFlagName);
							if (isEmpty(flag) || !flag.equals("1")) {
								sendErrorResponse(ctx, req, RetCodes.HTTP_NO_LOGIN);
								return;
							}
						}

						continue3(ctx, req);
					}
				});

				return;
			}
		}

		continue3(ctx, req);
	}

	void continue3(WebContextData ctx, DefaultWebReq req) {

		WebPlugin[] plugins = ctx.getRoute().getPlugins();

		// postsession
		if (plugins != null) {
			for (WebPlugin p : plugins) {
				if (p instanceof PostSessionPlugin) {
					int retCode = ((PostSessionPlugin) p).postSession(ctx, req);
					if (retCode != 0) {
						sendErrorResponse(ctx, req, retCode);
						return;
					}
				}
			}
		}

		// asyncpostsession
		if (plugins != null) {
			ArrayList<AsyncPostSessionPlugin> list = null;
			for (WebPlugin p : plugins) {
				if (p instanceof AsyncPostSessionPlugin) {
					if (list == null)
						list = new ArrayList<AsyncPostSessionPlugin>();
					list.add((AsyncPostSessionPlugin) p);
				}
			}
			if (list != null) {
				if (list.size() == 1) {

					list.get(0).asyncPostSession(ctx, req, new Continue<Integer>() {
						public void readyToContinue(Integer retCode) {
							
							ServerContext.set(ctx);
							
							if (retCode != 0) {
								sendErrorResponse(ctx, req, retCode);
								return;
							}
							continue4(ctx, req);
						}
					});

				} else {
					doMultiAsyncPostSession(ctx, req, list, 0);
				}
				return;
			}
		}

		continue4(ctx, req);
	}

	void doMultiAsyncPostSession(WebContextData ctx, DefaultWebReq req, ArrayList<AsyncPostSessionPlugin> list,
			int index) {
		if (index >= list.size()) {
			continue4(ctx, req);
			return;
		}

		list.get(index).asyncPostSession(ctx, req, new Continue<Integer>() {
			public void readyToContinue(Integer retCode) {
				
				ServerContext.set(ctx);
				
				if (retCode != 0) {
					sendErrorResponse(ctx, req, retCode);
					return;
				}
				doMultiAsyncPostSession(ctx, req, list, index + 1);
			}
		});
	}

	void continue4(WebContextData ctx, DefaultWebReq req) {

		Object service = serviceMetas.findService(ctx.getMeta().getServiceId());
		if (service != null) {
			callService(ctx, req, service);
			return;
		}

		Object referer = serviceMetas.findReferer(ctx.getMeta().getServiceId());
		if (referer != null) {
			callClient(ctx, req, referer);
			return;
		}

		Object asyncReferer = serviceMetas.findAsyncReferer(ctx.getMeta().getServiceId());
		if (asyncReferer != null) {
			callClient(ctx, req, asyncReferer);
			return;
		}

		callDynamic(ctx, req);
	}

	void callService(WebContextData ctx, DefaultWebReq req, Object service) {

		ExecutorManager em = executorManager;
		RpcCallable callable = serviceMetas.findCallable(service.getClass().getName());
		if (callable != null)
			em = callable.getExecutorManager();

		Message data = rpcDataConverter.generateData(ctx, req, false);
		if (data == null) {
			sendErrorResponse(ctx, req, RetCodes.ENCODE_REQ_ERROR);
			return;
		}

		req.freeMemory();

		// find a pool to execute the request
		if (em != null) {
			ThreadPoolExecutor pool = em.getExecutor(ctx.getMeta().getServiceId(), ctx.getMeta().getMsgId());
			if (pool != null) {
				callServiceInPool(pool, ctx, req, data);
				return;
			}
		}
		callService(ctx, req, data);
	}

	void callServiceInPool(ThreadPoolExecutor pool, WebContextData ctx, DefaultWebReq req, Message data) {
		try {
			pool.execute(new Runnable() {
				public void run() {
					
					ServerContext.set(ctx);
					
					callService(ctx, req, data);
				}
			});
		} catch (Exception e) {
			sendErrorResponse(ctx, req, RetCodes.QUEUE_FULL);
			log.error("queue is full");
			return;
		}
	}

	void callService(WebContextData ctx, DefaultWebReq req, Message data) {

		String connId = ctx.getConnId();

		if (!isConnected(connId)) {
			endReq(ctx,req,RetCodes.SERVER_CONNECTION_BROKEN);
			return; // connection is broken while waiting in runnable queue, just throw the request, no need to send response
		}

		long ts = ctx.elapsedMillisByNow();
		int clientTimeout = ctx.getMeta().getTimeout();
		if (clientTimeout > 0 && ts >= clientTimeout) {
			sendErrorResponse(ctx, req, RetCodes.QUEUE_TIMEOUT); // fast response
			return;
		}

		ctx.setContinue(new Continue<RpcClosure>() {
			public void readyToContinue(RpcClosure closure) {
				
				ServerContext.set(ctx);
				
				WebServer.this.callServiceEnd(ctx, req, closure);
			}
		});

		try {
			Message res = doCallService(ctx, req, data);
			if (res == null) return; // an async service or exception, do nothing
			RpcClosure closure = new RpcClosure(ctx, data, res);
			callServiceEnd(ctx, req, closure);
		} catch (Exception e) {
			sendErrorResponse(ctx, req, RetCodes.BUSINESS_ERROR);
			log.error("callService exception", e);
			Trace.logException(e);
			return;
		}
	}

	Message doCallService(WebContextData ctx, DefaultWebReq req, Message data) throws Exception {
		RpcMeta meta = ctx.getMeta();

		Object object = serviceMetas.findService(meta.getServiceId());
		if (object == null) {
			sendErrorResponse(ctx, req, RetCodes.NOT_FOUND);
			return null;
		}
		Method method = serviceMetas.findMethod(meta.getServiceId(), meta.getMsgId());
		if (method == null) {
			sendErrorResponse(ctx, req, RetCodes.NOT_FOUND);
			return null;
		}

		Message res = (Message) method.invoke(object, new Object[] { data });
		return res;
	}

	public void callServiceEnd(WebContextData ctx, DefaultWebReq req, RpcClosure closure) {
		int retCode = closure.getRetCode();
		if (retCode > 0)
			throw new RuntimeException("retCode>0 is not allowed");

		DefaultWebRes res = new DefaultWebRes(req, 200);
		rpcDataConverter.parseData(ctx, closure.getRes(), res);
		
		continue5(ctx, req, res);
	}

	void callClient(WebContextData ctx, DefaultWebReq req, Object referer) {
		RpcCallable callable = serviceMetas.findCallable(referer.getClass().getName());
		if (callable == null) {
			sendErrorResponse(ctx, req, RetCodes.HTTP_CLIENT_NOT_FOUND);
			log.error("callable not found, cls=" + referer.getClass().getName());
			return;
		}

		Message m = rpcDataConverter.generateData(ctx, req, false);
		if (m == null) {
			sendErrorResponse(ctx, req, RetCodes.ENCODE_REQ_ERROR);
			return;
		}

		req.freeMemory();
		
		CompletableFuture<Message> future = callable.callAsync(ctx.getMeta().getServiceId(), ctx.getMeta().getMsgId(),m);
		future.thenAccept((message) -> {
			
			ServerContext.set(ctx);
			
			DefaultWebRes res = new DefaultWebRes(req, 200);
			rpcDataConverter.parseData(ctx, message, res);
			continue5(ctx, req, res);
		});
	}

	void callDynamic(WebContextData ctx, DefaultWebReq req) {
		RpcCallable callable = serviceMetas.findDynamicCallable(ctx.getMeta().getServiceId());
		if (callable == null) {
			sendErrorResponse(ctx, req, RetCodes.HTTP_CLIENT_NOT_FOUND);
			log.error("callable not found, serviceId=" + ctx.getMeta().getServiceId());
			return;
		}

		Message m = rpcDataConverter.generateData(ctx, req, true);
		if (m == null) {
			sendErrorResponse(ctx, req, RetCodes.ENCODE_REQ_ERROR);
			return;
		}

		req.freeMemory();

		CompletableFuture<Message> future = callable.callAsync(ctx.getMeta().getServiceId(), ctx.getMeta().getMsgId(),m);
		future.thenAccept((message) -> {
			
			ServerContext.set(ctx);
			
			DefaultWebRes res = new DefaultWebRes(req, 200);
			rpcDataConverter.parseData(ctx, message, res);
			continue5(ctx, req, res);
		});
		
	}

	void continue5(WebContextData ctx, DefaultWebReq req, DefaultWebRes res) {
		if (res == null) {
			sendErrorResponse(ctx, req, RetCodes.DECODE_RES_ERROR);
			return;
		}

		int retCode = res.getRetCode();
		String retMsg = res.getRetMsg();

		if (retCode < 0 && isEmpty(retMsg) ) {
			if (errorMsgConverter != null) {
				retMsg = errorMsgConverter.getErrorMsg(retCode);
			}
			if ( !isEmpty(retMsg) ) {
				res.setRetMsg(retMsg);
			}
		}

		Route r = ctx.getRoute();
		WebPlugin[] plugins = r.getPlugins();

		// prerender
		if (plugins != null) {
			for (WebPlugin p : plugins) {
				if (p instanceof PreRenderPlugin) {
					((PreRenderPlugin) p).preRender(ctx, req, res);
				}
			}
		}

		// standard process
		starndardResultMapping(ctx, req, res);

		// render
		boolean hasRender = false;
		if (plugins != null) {
			for (WebPlugin p : plugins) {
				if (p instanceof RenderPlugin) {
					((RenderPlugin) p).render(ctx, req, res);
					hasRender = true;
				}
			}
		}
		if (!hasRender) {
			renderToJson(ctx, req, res);
		}

		// postrender
		if (plugins != null) {
			for (WebPlugin p : plugins) {
				if (p instanceof PostRenderPlugin) {
					((PostRenderPlugin) p).postRender(ctx, req, res);
				}
			}
		}

		httpTransport.send(ctx.getConnId(), res);
		
		WebClosure closure = new WebClosure(ctx,req,res);
		ctx.end();
		
		String status = res.getRetCode() == 0 ? "SUCCESS" : "ERROR";
		ctx.getTraceContext().serverSpanStopped(status);
		
		if( monitorService != null)
			monitorService.webReqDone(closure);		
	}

	void parseQueryString(DefaultWebReq req) {
		String queryString = req.getQueryString();
		if (!isEmpty(queryString)) {
			parseFormContent(queryString, req);
		}

		String contentType = req.getContentType();
		if (!isEmpty(contentType) && contentType.equals(MIMETYPE_FORM)) {
			String content = req.getContent();
			parseFormContent(content, req);
		}
	}

	void parseFormContent(String contentStr, DefaultWebReq req) {
		String charset = req.getCharSet();
		QueryStringDecoder d = new QueryStringDecoder(contentStr, Charset.forName(charset), false);
		for (Map.Entry<String, List<String>> en : d.parameters().entrySet()) {
			String key = en.getKey();
			List<String> value = en.getValue();
			if (value.size() == 1)
				req.getParameters().put(key, value.get(0));
			else
				req.getParameters().put(key, value);
		}
	}

	void parseJsonContent(DefaultWebReq req) {
		String contentType = req.getContentType();
		if (!isEmpty(contentType) && contentType.equals(MIMETYPE_JSON)) {
			Map<String, Object> map = Json.toMap(req.getContent());
			if (map != null) {
				req.getParameters().putAll(map);
			}
		}
	}

	void starndardResultMapping(WebContextData ctx, DefaultWebReq req, DefaultWebRes res) {
		TypeSafeMap results = new TypeSafeMap(res.getResults());
		
		if (results.contains(HttpCodeName)) {
			int v = results.intValue(HttpCodeName);
			if (v > 0)
				res.setHttpCode(v);
			results.remove(HttpCodeName);
		}
		if (results.contains(HttpContentTypeName)) {
			String s = results.stringValue(HttpContentTypeName);
			if (!s.isEmpty())
				res.setContentType(s);
			results.remove(HttpContentTypeName);
		}
		for (String key : results.keySet()) {
			if (key.startsWith(HeaderPrefix)) {
				String name0 = key.substring(HeaderPrefix.length());
				if( !name0.equalsIgnoreCase("contenttype") && !name0.equalsIgnoreCase("contentlength")  ) {
					String name = toHeaderName(name0);
					String value = results.stringValue(key);
					res.setHeader(name, value);
					results.remove(key);
				}
			}
			if (key.startsWith(CookiePrefix)) {
				String name =  key.substring(CookiePrefix.length()) ;
				if( !name.equals(sessionIdCookieName)) {
					String value = results.stringValue(key);
					res.addCookie(name, value);
					results.remove(key);
				}
			}
		}

		String sessionId = ctx.getSessionId();
		if (!hasSessionId(req) && !isEmpty(sessionId)) {
			if( isEmpty(sessionIdCookiePath) )
				res.addCookie(sessionIdCookieName, sessionId);
			else 
				res.addCookie(sessionIdCookieName, sessionId+"^path="+sessionIdCookiePath);
		}
    	
    	if( results.contains(SessionMapName) ) {
    		Map<String,Object> sessionMap = results.mapValue(SessionMapName);
    		if( sessionMap != null ) {
    			TypeSafeMap session = new TypeSafeMap(sessionMap);
    			String loginFlag = session.stringValue(LoginFlagName);
				if( !isEmpty(loginFlag) && loginFlag.equals("0") ) { // remove all session
					sessionService.remove(sessionId, null);
				} else {
					HashMap<String,String> values = new HashMap<>();
					for(Map.Entry<String, Object> i: sessionMap.entrySet() ) {
						values.put(i.getKey(), TypeSafe.anyToString( i.getValue() ) );
					}
					sessionService.update(sessionId, values, null);
				}
    		}
    		results.remove(SessionMapName);
    	}    	
    	
	}

	void renderToJson(WebContextData ctx, DefaultWebReq req, DefaultWebRes res) {
		res.setContentType(MIMETYPE_JSON);
		res.setContent(Json.toJson(res.getResults()));
	}

	public void connected(String connId, String localAddr) {
		clientConns.put(connId, localAddr);
	}

	public void disconnected(String connId) {
		clientConns.remove(connId);
	}

	public boolean isConnected(String connId) {
		return clientConns.containsKey(connId);
	}

	boolean hasSessionId(DefaultWebReq req) {
		String sessionId = req.getCookie(sessionIdCookieName);
		if (isEmpty(sessionId)) {
			return false;
		} else {
			return true;
		}
	}

	String getOrNewSessionId(DefaultWebReq req) {
		String sessionId = req.getCookie(sessionIdCookieName);
		if (isEmpty(sessionId)) {
			sessionId = UUID.randomUUID().toString().replaceAll("-", "");
		}
		return sessionId;
	}

	String getClientTraceId(DefaultWebReq req) {
		return req.getHeader(TraceIdName);
	}

	void sendErrorResponse(WebContextData ctx, DefaultWebReq req, int retCode) {
		DefaultWebRes res = generateError(req, retCode);
		httpTransport.send(ctx.getClientIp(), res);
		WebClosure closure = new WebClosure(ctx,req,res);
		ctx.end();
		
		String status = retCode == 0 ? "SUCCESS" : "ERROR";
		ctx.getTraceContext().serverSpanStopped(status);
		
		if( monitorService != null)
			monitorService.webReqDone(closure);
	}

	void endReq(WebContextData ctx, DefaultWebReq req, int retCode) {
		DefaultWebRes res = generateError(req, retCode);
		WebClosure closure = new WebClosure(ctx,req,res);
		ctx.end();
		
		String status = retCode == 0 ? "SUCCESS" : "ERROR";
		ctx.getTraceContext().serverSpanStopped(status);
		
		if( monitorService != null)
			monitorService.webReqDone(closure);
	}
	
	DefaultWebRes generateError(DefaultWebReq req, int retCode) {
		return generateError(req, retCode, 200);
	}

	DefaultWebRes generateError(DefaultWebReq req, int retCode, int httpCode) {
		DefaultWebRes res = new DefaultWebRes(req, httpCode);
		res.setContentType(MIMETYPE_JSON);
		String content = String.format(ContentFormat, retCode, RetCodes.retCodeText(retCode));
		res.setContent(content);
		return res;
	}

	int nextSequence() {
		int v = seq.incrementAndGet();
		if (v >= 100000000)
			seq.set(0);
		return v;
	}

	String getRemoteAddr(String connId) {
		int p = connId.lastIndexOf(":");
		return connId.substring(0, p);
	}

	String getRemoteIp(String connId) {
		int p = connId.indexOf(":");
		return connId.substring(0, p);
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}

	public HttpTransport getHttpTransport() {
		return httpTransport;
	}

	public void setHttpTransport(HttpTransport httpTransport) {
		this.httpTransport = httpTransport;
	}

	public FlowControl getFlowControl() {
		return flowControl;
	}

	public void setFlowControl(FlowControl flowControl) {
		this.flowControl = flowControl;
	}

	public SessionService getSessionService() {
		return sessionService;
	}

	public void setSessionService(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	public RpcDataConverter getRpcDataConverter() {
		return rpcDataConverter;
	}

	public void setRpcDataConverter(RpcDataConverter rpcDataConverter) {
		this.rpcDataConverter = rpcDataConverter;
	}

	public ServiceMetas getServiceMetas() {
		return serviceMetas;
	}

	public void setServiceMetas(ServiceMetas serviceMetas) {
		this.serviceMetas = serviceMetas;
	}

	public ExecutorManager getExecutorManager() {
		return executorManager;
	}

	public void setExecutorManager(ExecutorManager executorManager) {
		this.executorManager = executorManager;
	}

	public ErrorMsgConverter getErrorMsgConverter() {
		return errorMsgConverter;
	}

	public void setErrorMsgConverter(ErrorMsgConverter errorMsgConverter) {
		this.errorMsgConverter = errorMsgConverter;
	}

	public RouteService getRouteService() {
		return routeService;
	}

	public void setRouteService(RouteService routeService) {
		this.routeService = routeService;
	}

	public String getSessionIdCookieName() {
		return sessionIdCookieName;
	}

	public void setSessionIdCookieName(String sessionIdCookieName) {
		this.sessionIdCookieName = sessionIdCookieName;
	}

	public WebMonitorService getMonitorService() {
		return monitorService;
	}

	public void setMonitorService(WebMonitorService monitorService) {
		this.monitorService = monitorService;
	}

	public String getSessionIdCookiePath() {
		return sessionIdCookiePath;
	}

	public void setSessionIdCookiePath(String sessionIdCookiePath) {
		this.sessionIdCookiePath = sessionIdCookiePath;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
	}
}
