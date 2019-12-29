package krpc.rpc.web.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import krpc.common.*;
import krpc.rpc.core.*;
import krpc.rpc.core.proto.RpcMeta;
import krpc.rpc.util.IpUtils;
import krpc.rpc.util.TypeSafe;
import krpc.rpc.util.TypeSafeMap;
import krpc.rpc.web.*;
import krpc.trace.Span;
import krpc.trace.Trace;
import krpc.trace.TraceContext;
import krpc.trace.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static krpc.rpc.web.WebConstants.*;

public class WebServer implements HttpTransportCallback, InitClose, StartStop, AlarmAware, DumpPlugin, HealthPlugin {

    static Logger log = LoggerFactory.getLogger(WebServer.class);

    String sessionIdCookieName = DefaultSessionIdCookieName;
    String sessionIdCookiePath = "";
    int expireSeconds = 0;
    boolean autoTrim = true;

    SessionService defaultSessionService;

    ServiceMetas serviceMetas;
    ErrorMsgConverter errorMsgConverter;

    WebRouteService routeService;
    HttpTransport httpTransport;
    RpcDataConverter rpcDataConverter;
    Validator validator;
    ExecutorManager executorManager;
    WebMonitorService monitorService;

    AtomicInteger seq = new AtomicInteger(0);
    ConcurrentHashMap<String, String> clientConns = new ConcurrentHashMap<String, String>();

    ArrayList<Object> resources = new ArrayList<Object>();
    ConcurrentHashMap<String, Set<String>> cachedOrigins = new ConcurrentHashMap<>();

    Alarm alarm = new DummyAlarm();

    AtomicInteger queueFullErrorCount = new AtomicInteger();
    AtomicInteger lastQueueFullErrorCount = new AtomicInteger();

    public void init() {

        resources.add(defaultSessionService);
        resources.add(routeService);
        resources.add(httpTransport);
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

    private WebContextData generateCtx(String connId, DefaultWebReq req, WebRoute r) {

        int sequence = nextSequence();
        RpcMeta.Builder builder = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST)
                .setServiceId(r.getServiceId()).setMsgId(r.getMsgId()).setSequence(sequence);

        RpcMeta.Trace trace = generateTraceInfo(connId, req, r);

        RpcMeta meta = builder.setTrace(trace).build();
        WebContextData ctx = new WebContextData(connId, meta, r, Trace.currentContext());
        return ctx;
    }

    private RpcMeta.Trace generateTraceInfo(String connId, DefaultWebReq req, WebRoute r) {

        RpcMeta.Trace.Builder traceBuilder = RpcMeta.Trace.newBuilder();

        TraceIds traceIds = Trace.newStartTraceIds(true);
        traceBuilder.setTraceId(traceIds.getTraceId());
        traceBuilder.setParentSpanId(traceIds.getParentSpanId());
        traceBuilder.setSpanId(traceIds.getSpanId());

        traceBuilder.setSampleFlag(Trace.getSampleFlag());

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

        traceBuilder.setPeers(peers);

        String clientTraceId = getClientTraceId(req);
        if (!isEmpty(clientTraceId)) {
            traceBuilder.setTags("x-trace-id=" + clientTraceId);
        }

        String dyeing = getDyeing(req);
        if( isEmpty(dyeing) && !isEmpty(Dyeing.dyeingGlobal) ) {
            dyeing = Dyeing.dyeingGlobal;
        }
        if (!isEmpty(dyeing)) {
            traceBuilder.setDyeing(dyeing);
        }

        RpcMeta.Trace trace = traceBuilder.build();

        String action = serviceMetas.getName(r.getServiceId(), r.getMsgId());
        Span span = Trace.startForServer(trace, "HTTPSERVER", action);
        span.setRemoteAddr(getRemoteAddr(connId));

        return trace;
    }

    private void receiveStatic(String connId, DefaultWebReq req) {
        if (req.getMethod() == HttpMethod.GET || req.getMethod() == HttpMethod.HEAD) {
            File file = routeService.findStaticFile(req.getHostNoPort(), req.getPath());
            if (file != null) {
                boolean done = routeStaticFile(connId, req, file);
                if (done) return;
            }
        }

        DefaultWebRes res = generateError(req, RetCodes.HTTP_URL_NOT_FOUND, 404, null);
        httpTransport.send(connId, res);
    }

    public void receive(String connId, DefaultWebReq req,long receiveMicros) {

        if (req.getMethod() == HttpMethod.OPTIONS) {
            processCorsPreRequest(connId, req);
            return;
        }

        WebRoute r = routeService.findRoute(req.getHostNoPort(), req.getPath(), req.getMethod().toString());
        if (r == null) {
            receiveStatic(connId, req);
            return;
        }

        if (!checkCorsRequest(connId, req, r)) {
            DefaultWebRes res = generateError(req, RetCodes.HTTP_FORBIDDEN, 403, null);
            httpTransport.send(connId, res);
            return;
        }

        WebContextData ctx = generateCtx(connId, req, r);
        ctx.setDecodeMicros( ctx.getStartMicros() - receiveMicros );
        ServerContext.set(ctx);

        List<PreParsePlugin> ppps = r.getPreParsePlugins();

        if (ppps != null) {
            for (PreParsePlugin p : ppps) {
                int retCode = p.preParse(ctx, req);
                if (retCode != 0) {
                    sendErrorResponse(ctx, req, retCode);
                    return;
                }
            }
        }

        // asyncpreparse
        List<AsyncPreParsePlugin> apopps = r.getAsyncPreParsePlugins();
        if (apopps != null) {
            if (apopps.size() == 1) {
                apopps.get(0).asyncPreParse(ctx, req, new Continue<Integer>() {
                    public void readyToContinue(Integer retCode) {

                        ServerContext.set(ctx);

                        if (retCode != 0) {
                            sendErrorResponse(ctx, req, retCode);
                            return;
                        }
                        continue1(ctx, req);
                    }
                });

            } else {
                doMultiAsyncPreParse(ctx, req, apopps, 0);
            }
            return;
        }

        continue1(ctx, req);
    }

    void doMultiAsyncPreParse(WebContextData ctx, DefaultWebReq req, List<AsyncPreParsePlugin> list,
                              int index) {

        if (index >= list.size()) {
            continue1(ctx, req);
            return;
        }

        list.get(index).asyncPreParse(ctx, req, new Continue<Integer>() {
            public void readyToContinue(Integer retCode) {

                ServerContext.set(ctx);

                if (retCode != 0) {
                    sendErrorResponse(ctx, req, retCode);
                    return;
                }
                doMultiAsyncPreParse(ctx, req, list, index + 1);
            }
        });
    }

    public void continue1(WebContextData ctx, DefaultWebReq req) {

        WebRoute r = ctx.getRoute();

        if (r.getVariables() != null) {
            req.getParameters().putAll(r.getVariables());
        }

        // parse queryString, both in url or content
        parseQueryString(req);

        // parse
        ParserPlugin pp = r.getParserPlugin();
        if (pp != null) {
            int retCode = pp.parse(ctx, req);
            if (retCode != 0) {
                sendErrorResponse(ctx, req, retCode);
                return;
            }
        } else {
            parseJsonContent(req);
        }

        if (autoTrim) {
            trimReq(req);
        }

        // postparse
        List<PostParsePlugin> popps = r.getPostParsePlugins();
        if (popps != null) {
            for (PostParsePlugin p : popps) {
                int retCode = p.postParse(ctx, req);
                if (retCode != 0) {
                    sendErrorResponse(ctx, req, retCode);
                    return;
                }
            }
        }

        // asyncpostparse
        List<AsyncPostParsePlugin> apopps = r.getAsyncPostParsePlugins();
        if (apopps != null) {
            if (apopps.size() == 1) {
                apopps.get(0).asyncPostParse(ctx, req, new Continue<Integer>() {
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
                doMultiAsyncPostParse(ctx, req, apopps, 0);
            }
            return;
        }

        continue2(ctx, req);
    }

    void doMultiAsyncPostParse(WebContextData ctx, DefaultWebReq req, List<AsyncPostParsePlugin> list,
                               int index) {

        if (index >= list.size()) {
            continue2(ctx, req);
            return;
        }

        list.get(index).asyncPostParse(ctx, req, new Continue<Integer>() {
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

        WebRoute r = ctx.getRoute();

        if (r.getSessionMode() == WebRoute.SESSION_MODE_ID) {
            String sessionId = getOrNewSessionId(req);
            ctx.setSessionId(sessionId);
        }

        if (r.getSessionMode() == WebRoute.SESSION_MODE_YES || r.getSessionMode() == WebRoute.SESSION_MODE_OPTIONAL) {

            SessionService ss = defaultSessionService;
            if (r.getSessionServicePlugin() != null) ss = r.getSessionServicePlugin();

            if (ss == null) {
                sendErrorResponse(ctx, req, RetCodes.HTTP_NO_SESSIONSERVICE);
                return;
            }

            if (!hasSessionId(req) && r.getSessionMode() == WebRoute.SESSION_MODE_YES) {
                sendErrorResponse(ctx, req, RetCodes.HTTP_NO_LOGIN);
                return;
            }

            String sessionId = getOrNewSessionId(req);
            ctx.setSessionId(sessionId);

            HashMap<String, String> session = new HashMap<String, String>();
            ctx.setSession(session);

            if (hasSessionId(req)) {
                ss.load(sessionId, session, new Continue<Integer>() {
                    public void readyToContinue(Integer retCode) {

                        ServerContext.set(ctx);

                        if (retCode != 0) {
                            sendErrorResponse(ctx, req, retCode);
                            return;
                        }

                        if (r.getSessionMode() == WebRoute.SESSION_MODE_YES) {
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

        WebRoute r = ctx.getRoute();
        List<PostSessionPlugin> psp = r.getPostSessionPlugins();

        // postsession
        if (psp != null) {
            for (PostSessionPlugin p : psp) {

                int retCode = p.postSession(ctx, req);
                if (retCode != 0) {
                    sendErrorResponse(ctx, req, retCode);
                    return;
                }

            }
        }

        // asyncpostsession
        List<AsyncPostSessionPlugin> apsp = r.getAsyncPostSessionPlugins();

        if (apsp != null) {

            if (apsp.size() == 1) {

                apsp.get(0).asyncPostSession(ctx, req, new Continue<Integer>() {
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
                doMultiAsyncPostSession(ctx, req, apsp, 0);
            }
            return;

        }

        continue4(ctx, req);
    }

    void doMultiAsyncPostSession(WebContextData ctx, DefaultWebReq req, List<AsyncPostSessionPlugin> list,
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
        if (callable != null) {  // webserver -> server -> service   else:  webserver -> service
            em = callable.getExecutorManager();
        }

        Message msgReq = rpcDataConverter.generateData(ctx, req, false);
        if (msgReq == null) {
            sendErrorResponse(ctx, req, RetCodes.ENCODE_REQ_ERROR);
            return;
        }

        req.freeMemory();

        if (em != null) {
            ThreadPoolExecutor pool = em.getExecutor(ctx.getMeta().getServiceId(), ctx.getMeta().getMsgId());
            if (pool != null) {
                callServiceInPool(pool, ctx, req, msgReq);
                return;
            }
        }
        callService(ctx, req, msgReq);
    }

    void callServiceInPool(ThreadPoolExecutor pool, WebContextData ctx, DefaultWebReq req, Message msgReq) {
        try {
            pool.execute(new Runnable() {
                public void run() {
                    ServerContext.set(ctx);
                    callService(ctx, req, msgReq);
                }
            });
        } catch (Exception e) {
            queueFullErrorCount.incrementAndGet();
            sendErrorResponse(ctx, req, RetCodes.QUEUE_FULL);
            log.error("queue is full, e="+e.getMessage());
            return;
        }
    }

    void callService(WebContextData ctx, DefaultWebReq req, Message msgReq) {

        ctx.afterQueue();

        String connId = ctx.getConnId();

        if (!isConnected(connId)) {
            endReq(ctx, req, RetCodes.SERVER_CONNECTION_BROKEN);
            return; // connection is broken while waiting in runnable queue, just throw the request, no need to send response
        }

        long ts = ctx.elapsedMillisByNow();
        int clientTimeout = ctx.getMeta().getTimeout();
        if (clientTimeout > 0 && ts >= clientTimeout) {
            queueFullErrorCount.incrementAndGet();
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
            Message res = doCallService(ctx, req, msgReq);
            if (res == null) return; // an async service or exception, do nothing
            RpcClosure closure = new RpcClosure(ctx, msgReq, res);
            callServiceEnd(ctx, req, closure);
        } catch (Exception e) {
            String traceId = "no_trace_id";
            TraceContext tctx = Trace.currentContext();
            if( tctx != null ) {
                if( tctx.getTrace() != null ) {
                    traceId  = Trace.currentContext().getTrace().getTraceId();
                }
                Span rootSpan = tctx.rootSpan();
                if( rootSpan != null ) {
                    rootSpan.logException(e);
                }
            }

            log.error("callService exception, traceId=" + traceId, e);
            int retCode = RetCodes.getExceptionRetCode(e);

            // sendErrorResponse(ctx, req, RetCodes.BUSINESS_ERROR);
            String msg = RetCodes.retCodeText(retCode) + " in (" + ctx.getMeta().getServiceId() + ")";
            sendErrorResponse(ctx, req, retCode, msg);
        }
    }


    Message doCallService(WebContextData ctx, DefaultWebReq req, Message msgReq) throws Exception {
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

        if (validator != null) {
            ValidateResult result = validator.validate(msgReq);
            if (result != null) {
                sendErrorResponse(ctx, req, result.getRetCode(), result.getRetMsg());
                return null;
            }
        }

        ctx.setAttribute("pendingReq", msgReq);
        Message res = (Message) method.invoke(object, new Object[]{msgReq});
        return res;
    }

    public void callServiceEnd(WebContextData ctx, DefaultWebReq req, RpcClosure closure) {

        int retCode = closure.getRetCode();
        if (retCode > 0)
            throw new RuntimeException("retCode>0 is not allowed");

        if (!ctx.setReplied()) return;

        DefaultWebRes res = new DefaultWebRes(req, 200);
        rpcDataConverter.parseData(ctx, closure.asResMessage(), res);

        startRender(ctx, req, res);
    }

    void callClient(WebContextData ctx, DefaultWebReq req, Object referer) {

        ctx.afterQueue();

        RpcCallable callable = serviceMetas.findCallable(referer.getClass().getName());
        if (callable == null) {
            sendErrorResponse(ctx, req, RetCodes.HTTP_SERVICE_NOT_FOUND);
            log.error("callable not found, cls=" + referer.getClass().getName());
            return;
        }

        Message m = rpcDataConverter.generateData(ctx, req, false);
        if (m == null) {
            sendErrorResponse(ctx, req, RetCodes.ENCODE_REQ_ERROR);
            return;
        }

        req.freeMemory();

        addRetrier(ctx);
        CompletableFuture<Message> future = callable.callAsync(ctx.getMeta().getServiceId(), ctx.getMeta().getMsgId(), m);
        future.thenAccept((message) -> {

            ServerContext.set(ctx);

            DefaultWebRes res = new DefaultWebRes(req, 200);
            rpcDataConverter.parseData(ctx, message, res);
            startRender(ctx, req, res);
        });
    }

    void callDynamic(WebContextData ctx, DefaultWebReq req) {

        ctx.afterQueue();

        addRetrier(ctx);
        RpcCallable callable = serviceMetas.findDynamicCallable(ctx.getMeta().getServiceId());
        if (callable == null) {
            sendErrorResponse(ctx, req, RetCodes.HTTP_SERVICE_NOT_FOUND);
            log.error("callable not found, serviceId=" + ctx.getMeta().getServiceId());
            return;
        }

        Message m = rpcDataConverter.generateData(ctx, req, true);
        if (m == null) {
            sendErrorResponse(ctx, req, RetCodes.ENCODE_REQ_ERROR);
            return;
        }

        req.freeMemory();

        CompletableFuture<Message> future = callable.callAsync(ctx.getMeta().getServiceId(), ctx.getMeta().getMsgId(), m);
        future.thenAccept((message) -> {

            ServerContext.set(ctx);

            DefaultWebRes res = new DefaultWebRes(req, 200);
            rpcDataConverter.parseData(ctx, message, res);
            startRender(ctx, req, res);
        });

    }

    void addRetrier(WebContextData ctx) {
        try {
            String maxTimesStr = ctx.getRoute().getAttribute("retrierMaxTimes");
            if (maxTimesStr == null) {
                return;
            }
            String waitSecondsStr = ctx.getRoute().getAttribute("retrierWaitSeconds");
            if (waitSecondsStr == null) {
                return;
            }
            int maxTimes = Integer.parseInt(maxTimesStr);
            String[] ss = waitSecondsStr.split(",");
            int[] waitSeconds = new int[ss.length];
            for (int i = 0; i < ss.length; ++i) {
                waitSeconds[i] = Integer.parseInt(ss[i]);
            }
            ClientContext.setRetrier(maxTimes, waitSeconds);
        } catch (Throwable e) {
            log.error("retrier config not correct");
        }
    }

    void startRender(WebContextData ctx, DefaultWebReq req, DefaultWebRes res) {
        if (res == null) {
            sendErrorResponse(ctx, req, RetCodes.DECODE_RES_ERROR);
            return;
        }

        int retCode = res.getRetCode();

        if (retCode == 0 && res.getHttpCode() == 200) { // change the http code if defined in webroutes.xml, for restful 201 ...
            int httpCode = TypeSafe.anyToInt(ctx.getRoute().getAttribute("httpCode"));
            if (httpCode != 0) res.setHttpCode(httpCode);
        }

        String retMsg = res.getRetMsg();

        if (retCode < 0 && isEmpty(retMsg)) {
            if (errorMsgConverter != null) {
                retMsg = errorMsgConverter.getErrorMsg(retCode);
            }
            if (isEmpty(retMsg)) {
                retMsg = RetCodes.retCodeText(retCode);
            }
            if (!isEmpty(retMsg)) {
                res.setRetMsg(retMsg);
            }
        }

        WebRoute r = ctx.getRoute();
        List<PreRenderPlugin> prp = r.getPreRenderPlugins();

        // prerender
        if (prp != null) {
            for (PreRenderPlugin p : prp) {
                p.preRender(ctx, req, res);
            }
        }

        // standard process
        starndardResultMapping(ctx, req, res);

        // render
        RenderPlugin rp = r.getRenderPlugin();
        if (rp != null) {
            rp.render(ctx, req, res);
        } else {
            renderToJson(ctx, req, res);
        }

        // postrender
        List<PostRenderPlugin> porp = r.getPostRenderPlugins();
        if (porp != null) {
            for (PostRenderPlugin p : porp) {
                p.postRender(ctx, req, res);
            }
        }

        // 动态返回文件路径也支持断点续传
        if( res.getResults().containsKey(WebConstants.DOWNLOAD_FILE_FIELD) ) {
            String file = (String)res.getResults().get(WebConstants.DOWNLOAD_FILE_FIELD);
            File f = new File(file);
            if( f.exists() ) {
                String range = checkPartial(req, f);
                if (range != null) {
                    res.setHttpCode(206);
                    res.getResults().put(WebConstants.DOWNLOAD_FILE_RANGE_FIELD, range);
                }
            }
        }

        Span soutSpan = Trace.startAsync("OUTTS","send");
        boolean sendOk = httpTransport.send(ctx.getConnId(), res);
        soutSpan.stop(sendOk);

        res.setRetCode(retCode); // may be removed by plugin

        WebClosure closure = new WebClosure(ctx, req, res);
        ctx.end();

        //状态判断修改
        Span span = ctx.getTraceContext().stopForServer(retCode,retMsg);
        ctx.endWithTime(span.getTimeUsedMicros());

        if (monitorService != null)
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
        if (!isEmpty(contentType) && contentType.equals(MIMETYPE_JSON_NO_CHARSET) && !isEmpty(req.getContent())) {
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

        List<String> removeKeys = null;
        for (String key : results.keySet()) {
            if (key.startsWith(HeaderPrefix)) {
                String name0 = key.substring(HeaderPrefix.length());
                if (!name0.equalsIgnoreCase("contenttype") && !name0.equalsIgnoreCase("contentlength")) {
                    String name = WebUtils.toHeaderName(name0);
                    String value = results.stringValue(key);
                    res.setHeader(name, value);
                    if (removeKeys == null) removeKeys = new ArrayList<>();
                    removeKeys.add(key);
                }
            }
            if (key.startsWith(CookiePrefix)) {
                String name = key.substring(CookiePrefix.length());
                if (!name.equals(sessionIdCookieName)) {
                    String value = results.stringValue(key);
                    res.addCookie(name, value);
                    if (removeKeys == null) removeKeys = new ArrayList<>();
                    removeKeys.add(key);
                }
            }
        }
        if (removeKeys != null) {
            for (String key : removeKeys) {
                results.remove(key);
            }
        }

        String origin = req.getHeader("origin");
        if (!isEmpty(origin)) {
            res.setHeader("access-control-allow-origin", origin);
            res.setHeader("access-control-allow-methods", ctx.getRoute().getMethods().toUpperCase());
        }

        String sessionId = ctx.getSessionId();
        if (!hasSessionId(req) && !isEmpty(sessionId)) {
            if (isEmpty(sessionIdCookiePath))
                res.addCookie(sessionIdCookieName, sessionId);
            else
                res.addCookie(sessionIdCookieName, sessionId + "^path=" + sessionIdCookiePath);
        }

        if (results.contains(SessionMapName)) {

            WebRoute r = ctx.getRoute();
            SessionService ss = defaultSessionService;
            if (r.getSessionServicePlugin() != null) ss = r.getSessionServicePlugin();

            Map<String, Object> sessionMap = results.mapValue(SessionMapName);
            if (sessionMap != null) {
                TypeSafeMap session = new TypeSafeMap(sessionMap);
                String loginFlag = session.stringValue(LoginFlagName);
                if (!isEmpty(loginFlag) && loginFlag.equals("0")) { // remove all session
                    ss.remove(sessionId, null);
                } else {
                    HashMap<String, String> values = new HashMap<>();
                    for (Map.Entry<String, Object> i : sessionMap.entrySet()) {
                        values.put(i.getKey(), TypeSafe.anyToString(i.getValue()));
                    }
                    ss.update(sessionId, values, null);
                }
            }
            results.remove(SessionMapName);
        }

    }

    void renderToJson(WebContextData ctx, DefaultWebReq req, DefaultWebRes res) {
        res.setContentType(MIMETYPE_JSON);
        Map<String, Object> m = res.getResults();
        if (m.containsKey(WebConstants.DOWNLOAD_STREAM_FIELD)) {
            ByteString downloadStream = res.getByteStringResult(WebConstants.DOWNLOAD_STREAM_FIELD);
            if (downloadStream.isEmpty()) {
                m.remove(WebConstants.DOWNLOAD_STREAM_FIELD);
            }
        }
        res.setContent(Json.toJson(m));
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

    String getDyeing(DefaultWebReq req) {
        return req.getHeader(DyeingName);
    }


    void sendErrorResponse(WebContextData ctx, DefaultWebReq req, int retCode) {
        sendErrorResponse(ctx, req, retCode, null);
    }

    void sendErrorResponse(WebContextData ctx, DefaultWebReq req, int retCode, String retMsg) {

        if (!ctx.setReplied()) return;

        DefaultWebRes res = generateError(req, retCode, 200, retMsg);
        startRender(ctx, req, res);
    }

    void endReq(WebContextData ctx, DefaultWebReq req, int retCode) {
        DefaultWebRes res = generateError(req, retCode);
        WebClosure closure = new WebClosure(ctx, req, res);
        ctx.end();

        Span span = ctx.getTraceContext().stopForServer(retCode);
        ctx.endWithTime(span.getTimeUsedMicros());

        if (monitorService != null)
            monitorService.webReqDone(closure);
    }

    void trimReq(DefaultWebReq req) {
        Map<String, Object> m = req.getParameters();
        trimMap(m);
    }

    @SuppressWarnings("unchecked")
    void trimMap(Map<String, Object> m) {
        for (Map.Entry<String, Object> entry : m.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;

            if (value instanceof String) {
                value = ((String) value).trim();
                entry.setValue(value);
                continue;
            }

            if (value instanceof Map) {
                Map<String, Object> sub = (Map<String, Object>) value;
                trimMap(sub);
                continue;
            }

            if (value instanceof List) {
                List<Object> sub = (List<Object>) value;
                trimList(sub);
                continue;
            }

        }
    }

    @SuppressWarnings("unchecked")
    void trimList(List<Object> list) {
        for (int i = 0, size = list.size(); i < size; ++i) {
            Object item = list.get(i);
            if (item == null) continue;

            if (item instanceof String) {
                item = ((String) item).trim();
                list.set(i, item);
                continue;
            }

            if (item instanceof Map) {
                Map<String, Object> sub = (Map<String, Object>) item;
                trimMap(sub);
                continue;
            }

            if (item instanceof List) {
                List<Object> sub = (List<Object>) item;
                trimList(sub);
                continue;
            }
        }
    }

    DefaultWebRes generateError(DefaultWebReq req, int retCode) {
        return generateError(req, retCode, 200, null);
    }

    DefaultWebRes generateError(DefaultWebReq req, int retCode, int httpCode, String retMsg) {
        DefaultWebRes res = new DefaultWebRes(req, httpCode);
        res.setContentType(MIMETYPE_JSON);

        if (isEmpty(retMsg)) {
            if (errorMsgConverter != null) {
                retMsg = errorMsgConverter.getErrorMsg(retCode);
            }
            if (isEmpty(retMsg)) {
                retMsg = RetCodes.retCodeText(retCode);
            }
        }

        res.setRetCode(retCode);
        res.setRetMsg(retMsg);
        String content = String.format(ContentFormat, retCode, retMsg);
        res.setContent(content);
        return res;
    }

    int nextSequence() {
        int v = seq.incrementAndGet();
        if (v >= 100000000)
            seq.compareAndSet(v, 0);
        return v;
    }

    public boolean routeStaticFile(String connId, DefaultWebReq req, File file) {

        DefaultWebRes res = new DefaultWebRes(req, 200);
        res.getResults().put(WebConstants.DOWNLOAD_FILE_FIELD, file.getAbsolutePath()); // special key for file
        res.getResults().put(WebConstants.DOWNLOAD_EXPIRES_FIELD, expireSeconds);

        boolean send304 = check304(req, file);
        if (send304) {
            res.setHttpCode(304);
            httpTransport.send(connId, res);
            return true;
        }

        String range = checkPartial(req, file);
        if (range != null) {
            res.setHttpCode(206);
            res.getResults().put(WebConstants.DOWNLOAD_FILE_RANGE_FIELD, range);
            res.getResults().remove(WebConstants.DOWNLOAD_EXPIRES_FIELD);
            httpTransport.send(connId, res);
            return true;
        }

        httpTransport.send(connId, res);

        return true;
    }

    String checkPartial(DefaultWebReq req, File file) {

        String range = req.getHeaders().get(HttpHeaderNames.RANGE);
        if (isEmpty(range)) return null;

        long fileLength = file.length();

        try {
            String[] ss = range.trim().split("=");
            if (ss.length != 2) return null;
            if (!ss[0].equals("bytes")) return null;

            int p = ss[1].indexOf("-");
            if (p == -1) return null;

            String min = ss[1].substring(0, p);
            String max = ss[1].substring(p + 1);
            if (isEmpty(min) && isEmpty(max)) return null;

            if (isEmpty(min)) min = "0";
            if (isEmpty(max)) max = String.valueOf(fileLength - 1);

            if (Long.parseLong(min) > Long.parseLong(max)) return null;
            return min + "-" + max;
        } catch (Throwable e) {
            return null;
        }
    }

    boolean check304(DefaultWebReq req, File f) {
        String ifModifiedSince = req.getHeaders().get(HttpHeaderNames.IF_MODIFIED_SINCE);
        if (isEmpty(ifModifiedSince)) return check304Etag(req, f);
        Date ifModifiedSinceDate = WebUtils.parseDate(ifModifiedSince);
        if (ifModifiedSinceDate == null) return false;
        long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
        long fileLastModifiedSeconds = f.lastModified() / 1000;
        return (ifModifiedSinceDateSeconds == fileLastModifiedSeconds);
    }

    boolean check304Etag(DefaultWebReq req, File f) {
        String ifNoneMatch = req.getHeaders().get(HttpHeaderNames.IF_NONE_MATCH);
        if (isEmpty(ifNoneMatch)) return false;
        String etag = WebUtils.generateEtag(f);
        return etag.equals(ifNoneMatch);
    }

    void processCorsPreRequest(String connId, DefaultWebReq req) {

        String origin = req.getHeader("origin");
        String requestMethod = req.getHeader("access-control-request-method");
        String requestHeaders = req.getHeader("access-control-request-headers");
        WebRoute r = routeService.findRoute(req.getHostNoPort(), req.getPath(), requestMethod);
        if (r == null) {
            DefaultWebRes res = generateError(req, RetCodes.HTTP_FORBIDDEN, 403, null);
            httpTransport.send(connId, res);
            return;
        }

        String origins = r.getOrigins();
        boolean allowed = checkOrigins(origin, origins, req.getHostNoPort());
        if (!allowed) {
            DefaultWebRes res = generateError(req, RetCodes.HTTP_FORBIDDEN, 403, null);
            httpTransport.send(connId, res);
            return;
        }

        DefaultWebRes res = new DefaultWebRes(req, 204);
        res.setHeader("access-control-allow-origin", origin);
        res.setHeader("access-control-allow-methods", r.getMethods());
        if (!isEmpty(requestHeaders))
            res.setHeader("access-control-allow-headers", requestHeaders);
        res.setContentType(MIMETYPE_JSON);
        res.setContent("");
        httpTransport.send(connId, res);
    }

    boolean checkCorsRequest(String connId, DefaultWebReq req, WebRoute r) {
        String origin = req.getHeader("origin");
        if (isEmpty(origin)) return true;
        String origins = r.getOrigins();
        return checkOrigins(origin, origins, req.getHostNoPort());
    }

    boolean checkOrigins(String origin, String origins, String host) {
        if (origins.equals("*")) return true;
        String allowed = host;
        if (!isEmpty(origins)) allowed += "," + origins;
        String originHost = parseHost(origin);
        Set<String> set = strToSet(allowed);
        return set.contains(originHost);
    }

    String parseHost(String s) {
        int p1 = s.indexOf("://");
        if (p1 >= 0) p1 += 3;
        else p1 = 0;
        int p2 = s.indexOf(":", p1);
        if (p2 >= 0) return s.substring(p1, p2);
        p2 = s.indexOf(":", p1);
        if (p2 >= 0) return s.substring(p1, p2);
        return s.substring(p1);
    }

    Set<String> strToSet(String s) {
        Set<String> set = cachedOrigins.get(s);
        if (set != null) return set;
        set = new HashSet<>();
        String[] ss = s.split(",");
        for (String ts : ss) {
            if (ts.isEmpty()) continue;
            set.add(ts);
        }
        cachedOrigins.put(s, set);
        return set;
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

    public WebRouteService getRouteService() {
        return routeService;
    }

    public void setRouteService(WebRouteService routeService) {
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

    public Validator getValidator() {
        return validator;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public SessionService getDefaultSessionService() {
        return defaultSessionService;
    }

    public void setDefaultSessionService(SessionService defaultSessionService) {
        this.defaultSessionService = defaultSessionService;
    }

    public int getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(int expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public boolean isAutoTrim() {
        return autoTrim;
    }

    public void setAutoTrim(boolean autoTrim) {
        this.autoTrim = autoTrim;
    }

    @Override
    public void dump(Map<String, Object> metrics) {

        int n2 = queueFullErrorCount.getAndSet(0);
        lastQueueFullErrorCount.set(n2);
        metrics.put("krpc.webserver.queueFullErrorCount", n2);
        if (n2 > 0) {
            alarm.alarm(Alarm.ALARM_TYPE_QUEUEFULL, "krpc queue is full", "rpcqueue", IpUtils.localIp());
        }

        for (Object o : resources) {
            if (o == null) continue;
            if (o instanceof DumpPlugin) ((DumpPlugin) o).dump(metrics);
        }
    }

    @Override
    public void setAlarm(Alarm alarm) {
        this.alarm = alarm;
    }

    @Override
    public void healthCheck(List<HealthStatus> list) {
        int n2 = lastQueueFullErrorCount.get();
        if (n2 > 0) {
            String alarmId = alarm.getAlarmId(Alarm.ALARM_TYPE_QUEUEFULL);
            HealthStatus healthStatus = new HealthStatus(alarmId, false, "krpc queue is full", "rpcqueue", IpUtils.localIp());
            list.add(healthStatus);
        }
    }
}
