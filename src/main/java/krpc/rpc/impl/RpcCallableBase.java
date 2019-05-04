package krpc.rpc.impl;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import krpc.common.*;
import krpc.rpc.core.*;
import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.Span;
import krpc.trace.Trace;
import krpc.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class RpcCallableBase implements TransportCallback, DataManagerCallback, RpcCallable, Continue<RpcClosure>,
        InitClose, StartStop, AlarmAware, DumpPlugin, HealthPlugin {

    static Logger log = LoggerFactory.getLogger(RpcCallableBase.class);

    private static final String NO_CONNECTION_STR = "no_connection:0:0";

    ServiceMetas serviceMetas;
    MonitorService monitorService;
    ErrorMsgConverter errorMsgConverter;

    // for both
    Transport transport;

    // for client functions: as a referer
    DataManager dataManager;  // must not be null
    RpcFutureFactory futureFactory; // must not be null
    int retryQueueSize = 1000;
    ThreadPoolExecutor retryPool;

    RpcRetrier rpcRetrier;

    // for server functions: as a service
    ExecutorManager executorManager;
    Validator validator;
    FallbackPlugin fallbackPlugin;

    List<RpcPlugin> plugins = new ArrayList<>();

    HashSet<Integer> allowedServices = new HashSet<Integer>();
    HashSet<Integer> allowedReferers = new HashSet<Integer>();
    HashMap<String, Integer> timeoutMap = new HashMap<String, Integer>();
    HashMap<String, Integer> retryCountMap = new HashMap<String, Integer>();
    HashMap<String, Boolean> retryBrokenMap = new HashMap<String, Boolean>();

    ArrayList<Object> resources = new ArrayList<Object>();

    Alarm alarm = new DummyAlarm();

    AtomicInteger errorCount = new AtomicInteger();
    AtomicInteger lastErrorCount = new AtomicInteger();
    AtomicInteger queueFullErrorCount = new AtomicInteger();
    AtomicInteger lastQueueFullErrorCount = new AtomicInteger();

    abstract boolean isServerSide();

    abstract String nextConnId(ClientContextData ctx, Object req);

    abstract int nextSequence(String connId);

    abstract boolean isConnected(String connId);

    public boolean isRequest(RpcMeta meta) {
        return meta.getDirection() == RpcMeta.Direction.REQUEST;
    }

    public void init() {
        if (!isServerSide()) {
            NamedThreadFactory threadFactory2 = new NamedThreadFactory("krpc_retry_thread");
            retryPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(retryQueueSize), threadFactory2);
            retryPool.prestartAllCoreThreads();
        }

        resources.add(transport);
        resources.add(dataManager);
        resources.add(futureFactory);
        resources.add(executorManager);

        for (RpcPlugin p : plugins) {
            resources.add(p);
        }

        resources.add(rpcRetrier);

        InitCloseUtils.init(resources);
    }

    public void close() {
        InitCloseUtils.close(resources);

        if (retryPool != null)
            retryPool.shutdown();
    }

    public void start() {
        InitCloseUtils.start(resources);
    }

    public void stop() {
        InitCloseUtils.stop(resources);
    }

    public void addAllowedService(int serviceId) {
        allowedServices.add(serviceId);
    }

    public void addAllowedReferer(int serviceId) {
        allowedReferers.add(serviceId);
    }

    public HashSet<Integer> getReferers() {
        return allowedReferers;
    }

    public void addRetryPolicy(int serviceId, int msgId, int timeout, int retryCount, boolean retryBroken) {
        String key = serviceId + "." + msgId;
        if (timeoutMap.get(key) == null) {
            timeoutMap.put(key, timeout);
            retryCountMap.put(key, retryCount);
            retryBrokenMap.put(key, retryBroken);
        }
    }

    public void connected(String connId, String localAddr) {
    }

    public void disconnected(String connId) {
        if (dataManager != null)
            dataManager.disconnected(connId);
    }

    public Message call(int serviceId, int msgId, Message req) {
        int timeout = ClientContext.getTimeout();
        if (timeout <= 0) timeout = getTimeout(serviceId, msgId);
        try {
            CompletableFuture<Message> future = callAsyncInner(serviceId, msgId, req, false);
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return serviceMetas.generateRes(serviceId, msgId, RetCodes.RPC_TIMEOUT);
        } catch (InterruptedException e) {
            return serviceMetas.generateRes(serviceId, msgId, RetCodes.USER_CANCEL);
        } catch (Exception e) {
            log.error("exception", e);
            return serviceMetas.generateRes(serviceId, msgId, RetCodes.EXEC_EXCEPTION);
        }
    }

    public CompletableFuture<Message> callAsync(int serviceId, int msgId, Message req) {
        return callAsyncInner(serviceId, msgId, req, true);
    }

    private CompletableFuture<Message> callAsyncInner(int serviceId, int msgId, Message req, boolean isAsync) {

        RpcMeta.Builder builder = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST).setServiceId(serviceId).setMsgId(msgId);

        int timeout = ClientContext.removeTimeout();
        if (timeout > 0)
            builder.setTimeout(timeout);
        else
            builder.setTimeout(getTimeout(serviceId, msgId));

        String clientAttachment = ClientContext.removeAttachment();
        if (clientAttachment != null)
            builder.setAttachment(clientAttachment);

        String action = serviceMetas.getName(serviceId, msgId);
        if( action == null ) action = serviceId + "." + msgId;
        Span span = Trace.startAsync("RPCCLIENT", action);
        TraceContext tctx = Trace.currentContext();

        String connId = NO_CONNECTION_STR;
        span.setRemoteAddr(getAddr(connId));

        RpcMeta.Trace trace = generateTraceInfo(tctx, span);

        RpcMeta meta = builder.setTrace(trace).build();
        ClientContextData ctx = new ClientContextData(NO_CONNECTION_STR, meta, tctx, span);
        CompletableFuture<Message> future = futureFactory.newFuture(meta.getServiceId(), meta.getMsgId(), isAsync, ctx.getTraceContext());
        ctx.setFuture(future);
        ClientContext.set(ctx); // user code can call RpcClientContext.get() to obtain call information
        RpcClosure closure = new RpcClosure(ctx, req, false);

        ClientContext.RetrierInfo retrierInfo = ClientContext.removeRetrier();
        if( retrierInfo != null ) {

            RpcRetryTask task = new RpcRetryTask();
            task.setServiceId(closure.getCtx().getMeta().getServiceId());
            task.setMsgId(closure.getCtx().getMeta().getMsgId());
            task.setMessage(closure.asReqMessage());
            task.setMaxTimes(retrierInfo.maxTimes);
            task.setWaitSeconds(retrierInfo.waitSeconds);
            task.setTimeout(timeout);
            task.setAttachement(clientAttachment);

            closure.asClientCtx().setAttribute("retryTask",task);
        }

        if (!allowedReferers.contains(serviceId)) {
            endCall(closure, RetCodes.REFERER_NOT_ALLOWED);
            return future;
        }

        connId = nextConnId(ctx, req);
        if (connId == null || connId.equals(NO_CONNECTION_STR) ) { // no connection, no need to retry

            if (fallbackPlugin != null) {
                Message res = fallbackPlugin.fallback(ctx, req);
                if (res != null) {
                    endCall(closure, res);
                    return future;
                }
            }

            errorCount.incrementAndGet();
            endCall(closure, RetCodes.NO_CONNECTION);
            return future;
        }

        ctx.setConnId(connId);
        int sequence = nextSequence(connId);
        ReflectionUtils.updateSequence(meta, sequence);
        span.setRemoteAddr(getAddr(connId));

        if (plugins.size() > 0) {
            List<RpcPlugin> calledPlugins = new ArrayList<>();
            ctx.setAttribute("calledClientPlugins", calledPlugins);
            for (RpcPlugin p : plugins) {
                int retCode = p.preCall(ctx, req);
                if (retCode != 0) {
                    endCall(closure, retCode);
                    return future;
                }
                calledPlugins.add(p);
            }
        }

        sendCall(closure, true);
        return future;
    }

    public CompletableFuture<RpcRawMessage> callAsyncInner(int serviceId, int msgId, ByteBuf req, boolean isAsync) {

        RpcMeta.Builder builder = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST).setServiceId(serviceId).setMsgId(msgId);

        int timeout = ClientContext.removeTimeout();
        if (timeout > 0)
            builder.setTimeout(timeout);
        else
            builder.setTimeout(getTimeout(serviceId, msgId));

        String clientAttachment = ClientContext.removeAttachment();
        if (clientAttachment != null)
            builder.setAttachment(clientAttachment);

        String action = serviceMetas.getName(serviceId, msgId);
        if( action == null ) action = serviceId + "." + msgId;
        Span span = Trace.startAsync("RPCCLIENT", action);
        TraceContext tctx = Trace.currentContext();

        String connId = NO_CONNECTION_STR;
        span.setRemoteAddr(getAddr(connId));

        RpcMeta.Trace trace = generateTraceInfo(tctx, span);

        RpcMeta meta = builder.setTrace(trace).build();
        ClientContextData ctx = new ClientContextData(NO_CONNECTION_STR, meta, tctx, span);
        CompletableFuture<RpcRawMessage> future = futureFactory.newRawFuture(meta.getServiceId(), meta.getMsgId(), isAsync, ctx.getTraceContext());
        ctx.setFuture(future);
        ClientContext.set(ctx); // user code can call RpcClientContext.get() to obtain call information
        RpcClosure closure = new RpcClosure(ctx, req, true);

//        ClientContext.RetrierInfo retrierInfo = ClientContext.removeRetrier(); // todo
//        if( retrierInfo != null ) {
//
//            RpcRetryTask task = new RpcRetryTask();
//            task.setServiceId(closure.getCtx().getMeta().getServiceId());
//            task.setMsgId(closure.getCtx().getMeta().getMsgId());
//            task.setMessage(closure.getReq());
//            task.setMaxTimes(retrierInfo.maxTimes);
//            task.setWaitSeconds(retrierInfo.waitSeconds);
//            task.setTimeout(timeout);
//            task.setAttachement(clientAttachment);
//
//            closure.asClientCtx().setAttribute("retryTask",task);
//        }

        if (!allowedReferers.contains(serviceId)) {
            endCall(closure, RetCodes.REFERER_NOT_ALLOWED);
            return future;
        }

        connId = nextConnId(ctx, req);
        if (connId == null || connId.equals(NO_CONNECTION_STR) ) { // no connection, no need to retry

//            if (fallbackPlugin != null) { // todo
//                Message res = fallbackPlugin.fallback(ctx, req);
//                if (res != null) {
//                    endCall(closure, res);
//                    return future;
//                }
//            }

            errorCount.incrementAndGet();
            endCall(closure, RetCodes.NO_CONNECTION);
            return future;
        }

        ctx.setConnId(connId);
        int sequence = nextSequence(connId);
        ReflectionUtils.updateSequence(meta, sequence);
        span.setRemoteAddr(getAddr(connId));

//        if (plugins.size() > 0 ) { // todo
//            List<RpcPlugin> calledPlugins = new ArrayList<>();
//            ctx.setAttribute("calledClientPlugins", calledPlugins);
//            for (RpcPlugin p : plugins) {
//                int retCode = p.preCall(ctx, req);
//                if (retCode != 0) {
//                    endCall(closure, retCode);
//                    return future;
//                }
//                calledPlugins.add(p);
//            }
//        }

        sendCall(closure, true);
        return future;
    }

    private RpcMeta.Trace generateTraceInfo(TraceContext tctx, Span span) {
        RpcMeta.Trace.Builder traceBuilder = RpcMeta.Trace.newBuilder();
        Trace.inject(tctx, span, traceBuilder);
        traceBuilder.setPeers(tctx.getTrace().getPeers());
        traceBuilder.setSampleFlag(tctx.getTrace().getSampleFlag());
        return traceBuilder.build();
    }

    void sendCall(RpcClosure closure, boolean allowRetry) {

        RpcMeta meta = closure.getCtx().getMeta();

        try {
            dataManager.add(closure);

            RpcData data;
            if( !closure.isRaw() )
                data = new RpcData(meta, closure.asReqMessage());
            else
                data = new RpcData(meta, closure.asReqByteBuf());
            boolean ok = transport.send(closure.getCtx().getConnId(), data);
            if (!ok) { // safe if retry
                if (allowRetry) {
                    if (retryCall(closure)) return;
                }
                dataManager.remove(closure);
                endCall(closure, RetCodes.SEND_FAILED);
            }
        } catch (RpcException e) {  // encode error, no need to support retry
            dataManager.remove(closure);
            endCall(closure, e.getRetCode());
        }
    }

    boolean retryCall(final RpcClosure closure) {

        if (isServerSide()) return false;
        if (retryPool == null) return false;

        final RpcMeta meta = closure.getCtx().getMeta();
        final int retryCount = getRetryCount(meta.getServiceId(), meta.getMsgId());
        if (retryCount == 0 || closure.asClientCtx().getRetryTimes() >= retryCount) return false;
        closure.asClientCtx().incRetryTimes(closure.getCtx().getConnId());
        final String newConnId = nextConnId(closure.asClientCtx(), closure.reqData());
        if (newConnId == null || newConnId.equals(NO_CONNECTION_STR)  ) return false;

        try {
            retryPool.execute(new Runnable() {
                public void run() {

                    int newSequence = nextSequence(newConnId);
                    RpcMeta newMeta = meta.toBuilder().setSequence(newSequence).build();
                    closure.getCtx().setConnId(newConnId);
                    closure.getCtx().setMeta(newMeta);
                    closure.asClientCtx().getSpan().setRemoteAddr(getAddr(newConnId));

                    sendCall(closure, closure.asClientCtx().getRetryTimes() < retryCount); // recursive call sendClosure
                }
            });

            return true;
        } catch (Exception e) {
            log.error("rpcclient retry pool is full");
            return false;
        }
    }

    public boolean isExchange(String connId, RpcMeta meta) {
        boolean isExchange = serviceMetas.isExchangeServiceId(meta.getServiceId());
        if( !isExchange ) return false;

        if (isRequest(meta)) {
            return true;
        } else {
            RpcClosure closure = dataManager.get(connId, meta.getSequence());
            if (closure == null) return true;
            return closure.isRaw();
        }
    }

    public void receive(String connId, RpcData data) {

        if (isRequest(data.getMeta())) {

            RpcMeta meta = data.getMeta();
            String action = serviceMetas.getName(meta.getServiceId(), meta.getMsgId());
            if( action == null ) action = meta.getServiceId() + "."+ meta.getMsgId(); // may be empty in exchange mode
            Trace.startForServer(meta.getTrace(), "RPCSERVER", action);
            Trace.setRemoteAddr(getAddr(connId));
            ServerContextData ctx = new ServerContextData(connId, data.getMeta(), Trace.currentContext());
            ServerContext.set(ctx);

            if( serviceMetas.isExchangeServiceId(data.getMeta().getServiceId())) {
                RpcCallable callable = serviceMetas.findCallable(data.getMeta().getServiceId());
                if( callable == null || !(callable instanceof RpcClient) ) {
                    sendErrorResponse(ctx, data.asByteBuf(), RetCodes.SERVICE_NOT_ALLOWED);
                    log.error("not exchange serviceId, serviceId="+data.getMeta().getServiceId());
                    return;
                }

                doExchange(ctx,data,callable);
                return;
            }

            if (!allowedServices.contains(data.getMeta().getServiceId())) {
                sendErrorResponse(ctx, data.asMessage(), RetCodes.SERVICE_NOT_ALLOWED);
                log.error("service id is not allowed, serviceId=" + data.getMeta().getServiceId());
                return;
            }

            continue1(ctx, data);

        } else {

            RpcClosure closure = dataManager.remove(connId, data.getMeta().getSequence());
            if (closure == null) return; // data removed, ignore

            int retCode = data.getMeta().getRetCode();
            if (RetCodes.canSafeRetry(retCode)) { // safe if retry
                if (retryCall(closure)) return;
            }

            Object res = data.getBody();
            if( !closure.isRaw() ) {
                endCall(closure, res);
            } else {
                endCall(closure, new RpcRawMessage(retCode,(ByteBuf)res));
            }
        }
    }

    public void timeout(RpcClosure closure) {
        RpcMeta meta = closure.getCtx().getMeta();

        // donot support retry, the caller may use ClientContext.setRetrier( ... ) method to retry

        endCall(closure, RetCodes.RPC_TIMEOUT);
    }

    public void disconnected(RpcClosure closure) {
        RpcMeta meta = closure.getCtx().getMeta();

        boolean retryBroken = getRetryBroken(meta.getServiceId(),meta.getMsgId());
        if( retryBroken ) {
            if (retryCall(closure)) return;
        }

        endCall(closure, RetCodes.CONNECTION_BROKEN);
    }

    void endCall(RpcClosure closure, int retCode) {
        RpcMeta meta = closure.getCtx().getMeta();
        Object res;
        if(!closure.isRaw())
            res = serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), retCode);
        else
            res = new RpcRawMessage(retCode,Unpooled.EMPTY_BUFFER);
        endCall(closure, res);
    }

    void endCall(RpcClosure closure, Object res) {

        List<RpcPlugin> calledPlugins = (List<RpcPlugin>) closure.getCtx().getAttribute("calledClientPlugins");
        if (calledPlugins != null && !closure.isRaw() ) {
            for (RpcPlugin p : calledPlugins) {
                p.postCall(closure.getCtx(), closure.asReqMessage(), closure.asResMessage());
            }
        }

        if(!closure.isRaw())
            closure.done((Message)res);
        else
            closure.done((RpcRawMessage)res);

        closure.asClientCtx().getFuture().complete(res);

        RpcRetryTask retryTask = (RpcRetryTask) closure.getCtx().getAttribute("retryTask");
        if (retryTask != null && !closure.isRaw() ) {
            rpcRetrier.submit(closure.getRetCode(),retryTask);
        }

        String status = closure.getRetCode() == 0 ? "SUCCESS" : "ERROR";
        closure.asClientCtx().getSpan().stop(status);
        if (monitorService != null) {
            monitorService.callDone(closure);
        }
    }

    public String getAddr(String connId) {
        if (connId == null) connId = NO_CONNECTION_STR;
        int p = connId.lastIndexOf(":");
        if (p < 0) return connId;
        return connId.substring(0, p);
    }

    private void continue1(ServerContextData ctx, final RpcData data) {

        // find a pool to execute the request
        if (executorManager != null) {
            ThreadPoolExecutor pool = executorManager.getExecutor(data.getMeta().getServiceId(), data.getMeta().getMsgId());
            if (pool != null) {
                callServiceInPool(pool, ctx, data);
                return;
            }
        }
        callService(ctx, data);
    }

    private void callServiceInPool(ThreadPoolExecutor pool, ServerContextData ctx, final RpcData data) {
        try {
            pool.execute(new Runnable() {
                public void run() {
                    ctx.afterQueue();
                    ServerContext.set(ctx);

                    callService(ctx, data);
                }
            });
        } catch (Exception e) {
            queueFullErrorCount.incrementAndGet();
            sendErrorResponse(ctx, data.asMessage(), RetCodes.QUEUE_FULL);
            log.error("queue is full");
            return;
        }
    }
    private void doExchange(ServerContextData ctx, RpcData data, RpcCallable callable) {
        RpcClient client = (RpcClient) callable;
        RpcMeta meta = ctx.getMeta();
        CompletableFuture<RpcRawMessage> future = client.callAsyncInner(meta.getServiceId(),meta.getMsgId(),data.asByteBuf(),true);
        RpcClosure closure = new RpcClosure(ctx,data.asByteBuf(),true);
        ctx.setContinue(this);
        future.thenAccept((rawRes)->{
            ServerContext.set(ctx);
            closure.done(rawRes);
        });
    }

    private void callService(ServerContextData ctx, RpcData data) {

        String connId = ctx.getConnId();
        RpcMeta meta = ctx.getMeta();
        Message req = data.asMessage();

        if (!isConnected(connId)) {
            RpcClosure closure = new RpcClosure(ctx, req, false);
            endReq(closure, RetCodes.SERVER_CONNECTION_BROKEN);
            return;    // connection is broken while waiting in runnable queue, just throw the request, no need to send response
        }

        long ts = ctx.elapsedMillisByNow();
        int clientTimeout = ctx.getMeta().getTimeout();
        if (clientTimeout > 0 && ts >= clientTimeout) {
            queueFullErrorCount.incrementAndGet();
            sendErrorResponse(ctx, req, RetCodes.QUEUE_TIMEOUT); // waiting too long, fast response with a TIMEOUT_EXPIRED
            return;
        }

        Object object = serviceMetas.findService(meta.getServiceId());
        if (object == null) {
            sendErrorResponse(ctx, req, RetCodes.NOT_FOUND);
            return;
        }
        Method method = serviceMetas.findMethod(meta.getServiceId(), meta.getMsgId());
        if (method == null) {
            sendErrorResponse(ctx, req, RetCodes.NOT_FOUND);
            return;
        }

        ctx.setContinue(this);
        try {

            if (plugins.size() > 0) {
                List<RpcPlugin> calledPlugins = new ArrayList<>();
                ctx.setAttribute("calledServerPlugins", calledPlugins);
                for (RpcPlugin p : plugins) {
                    int retCode = p.preCall(ctx, req);
                    if (retCode != 0) {
                        sendErrorResponse(ctx, req, retCode);
                        return;
                    }
                    calledPlugins.add(p);
                }
            }

            if (validator != null) {
                String result = validator.validate(req);
                if (result != null) {
                    String message = RetCodes.retCodeText(RetCodes.VALIDATE_ERROR) + result;
                    sendErrorResponse(ctx, req, RetCodes.VALIDATE_ERROR, message);
                    return;
                }
            }

            ctx.setAttribute("pendingReq",req);

            Message res = (Message) method.invoke(object, new Object[]{req});
            if (res == null) return; // an async service or exception, do nothing

            RpcClosure closure = new RpcClosure(ctx, req, res);
            readyToContinue(closure);
        } catch (Exception e) {
            String traceId = "no_trace_id";
            if( Trace.currentContext() != null && Trace.currentContext().getTrace() != null )
                traceId  = Trace.currentContext().getTrace().getTraceId();
            log.error("callService exception, traceId="+traceId, e);
            Trace.logException(e);
            String msg = RetCodes.retCodeText(RetCodes.BUSINESS_ERROR)+" in ("+meta.getServiceId()+")";
            sendErrorResponse(ctx, data.asMessage(), RetCodes.BUSINESS_ERROR,msg);
        }
    }

    public void readyToContinue(RpcClosure closure) {

        String connId = closure.getCtx().getConnId();
        RpcMeta reqMeta = closure.getCtx().getMeta();
        int retCode = closure.getRetCode();
        if (retCode > 0) throw new RuntimeException("retCode>0 is not allowed");

        if( !closure.asServerCtx().setReplied() ) return;

        String retMsg = closure.getRetMsg();

        if(!closure.isRaw()) {
            if (retCode < 0 && (retMsg == null || retMsg.isEmpty())) {
                if (errorMsgConverter != null)
                    retMsg = errorMsgConverter.getErrorMsg(retCode);
                if (retMsg != null && retMsg.length() > 0) {
                    ReflectionUtils.setRetMsg(closure.asResMessage(), retMsg);
                }
            }
        }

        RpcMeta resMeta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.RESPONSE).setServiceId(reqMeta.getServiceId()).setMsgId(reqMeta.getMsgId()).setEncrypt(reqMeta.getEncrypt()).setSequence(reqMeta.getSequence()).setRetCode(retCode).build();
        if(!closure.isRaw())
            transport.send(connId, new RpcData(resMeta, closure.asResMessage()));
        else
            transport.send(connId, new RpcData(resMeta, closure.asResByteBuf()));

        endReq(closure);
    }

    void sendErrorResponse(RpcContextData ctx, ByteBuf req, int retCode) {
        if( !((ServerContextData)ctx).setReplied() ) return;

        RpcMeta reqMeta = ctx.getMeta();

        RpcMeta resMeta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.RESPONSE).setServiceId(reqMeta.getServiceId()).setMsgId(reqMeta.getMsgId()).setEncrypt(reqMeta.getEncrypt()).setSequence(reqMeta.getSequence()).setRetCode(retCode).build();
        RpcData data = new RpcData(resMeta);
        transport.send(ctx.getConnId(), data);

        RpcClosure closure = new RpcClosure(ctx, req, true);
        endReq(closure, retCode);
    }

    void sendErrorResponse(RpcContextData ctx, Message req, int retCode) {
        sendErrorResponse(ctx, req, retCode, null);
    }

    void sendErrorResponse(RpcContextData ctx, Message req, int retCode, String retMsg) {

        if( !((ServerContextData)ctx).setReplied() ) return;

        RpcMeta reqMeta = ctx.getMeta();

        RpcMeta resMeta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.RESPONSE).setServiceId(reqMeta.getServiceId()).setMsgId(reqMeta.getMsgId()).setEncrypt(reqMeta.getEncrypt()).setSequence(reqMeta.getSequence()).setRetCode(retCode).build();
        RpcData data = null;
        if (retMsg != null) {
            Message res = serviceMetas.generateRes(reqMeta.getServiceId(), reqMeta.getMsgId(), retCode, retMsg);
            data = new RpcData(resMeta, res);
        } else {
            data = new RpcData(resMeta);
        }
        transport.send(ctx.getConnId(), data);

        RpcClosure closure = new RpcClosure(ctx, req, false);
        endReq(closure, retCode);
    }

    void endReq(RpcClosure closure, int retCode) {

        List<RpcPlugin> calledPlugins = (List<RpcPlugin>) closure.getCtx().getAttribute("calledServerPlugins");
        if (calledPlugins != null && !closure.isRaw() ) {
            for (RpcPlugin p : calledPlugins) {
                p.postCall(closure.getCtx(), closure.asReqMessage(), closure.asResMessage());
            }
        }

        String status = !RetCodes.isSystemError(retCode) ? "SUCCESS" : "ERROR";
        closure.asServerCtx().getTraceContext().stopForServer(status);

        if (monitorService == null) return;
        RpcMeta meta = closure.getCtx().getMeta();
        if( !closure.isRaw() ) {
            Message res = serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), retCode);
            closure.done(res);
        } else {
            RpcRawMessage rawRes = new RpcRawMessage(retCode,Unpooled.EMPTY_BUFFER);
            closure.done(rawRes);
        }
        monitorService.reqDone(closure);
    }

    void endReq(RpcClosure closure) {
        String status = !RetCodes.isSystemError(closure.getRetCode()) ? "SUCCESS" : "ERROR";
        closure.asServerCtx().getTraceContext().stopForServer(status);

        if (monitorService == null) return;
        monitorService.reqDone(closure);
    }

    int getTimeout(int serviceId, int msgId) {
        Integer timeout = timeoutMap.get(serviceId + "." + msgId);
        if (timeout == null) timeout = timeoutMap.get(serviceId + ".-1");
        if (timeout == null) timeout = timeoutMap.get("-1.-1");
        return timeout == null ? 3000 : timeout;
    }

    int getRetryCount(int serviceId, int msgId) {
        Integer retryCount = retryCountMap.get(serviceId + "." + msgId);
        if (retryCount == null) retryCount = retryCountMap.get(serviceId + ".-1");
        if (retryCount == null) retryCount = retryCountMap.get("-1.-1");
        return retryCount == null ? 0 : retryCount;
    }

    boolean getRetryBroken(int serviceId, int msgId) {
        Boolean retryBroken = retryBrokenMap.get(serviceId + "." + msgId);
        if (retryBroken == null) retryBroken = retryBrokenMap.get(serviceId + ".-1");
        if (retryBroken == null) retryBroken = retryBrokenMap.get("-1.-1");
        return retryBroken == null ? false : retryBroken;
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public RpcFutureFactory getFutureFactory() {
        return futureFactory;
    }

    public void setFutureFactory(RpcFutureFactory futureFactory) {
        this.futureFactory = futureFactory;
    }

    public ServiceMetas getServiceMetas() {
        return serviceMetas;
    }

    public void setServiceMetas(ServiceMetas serviceMetas) {
        this.serviceMetas = serviceMetas;
    }

    public MonitorService getMonitorService() {
        return monitorService;
    }

    public void setMonitorService(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    public ExecutorManager getExecutorManager() {
        return executorManager;
    }

    public void setExecutorManager(ExecutorManager executorManager) {
        this.executorManager = executorManager;
    }

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public ErrorMsgConverter getErrorMsgConverter() {
        return errorMsgConverter;
    }

    public void setErrorMsgConverter(ErrorMsgConverter errorMsgConverter) {
        this.errorMsgConverter = errorMsgConverter;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public void setDataManager(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public int getRetryQueueSize() {
        return retryQueueSize;
    }

    public void setRetryQueueSize(int retryQueueSize) {
        this.retryQueueSize = retryQueueSize;
    }

    public Validator getValidator() {
        return validator;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public List<RpcPlugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<RpcPlugin> plugins) {
        this.plugins = plugins;
    }

    public FallbackPlugin getFallbackPlugin() {
        return fallbackPlugin;
    }

    public void setFallbackPlugin(FallbackPlugin fallbackPlugin) {
        this.fallbackPlugin = fallbackPlugin;
    }

    public RpcRetrier getRpcRetrier() {
        return rpcRetrier;
    }

    public void setRpcRetrier(RpcRetrier rpcRetrier) {
        this.rpcRetrier = rpcRetrier;
    }

    @Override
    public void dump(Map<String, Object> metrics) {

        int n = errorCount.getAndSet(0);
        lastErrorCount.set(n);
        metrics.put("krpc.client.curErrorCount",n);
        if( n > 0 ) {
            alarm.alarm(Alarm.ALARM_TYPE_RPCSERVER,"krpc no connection");
        }

        int n2 = queueFullErrorCount.getAndSet(0);
        lastQueueFullErrorCount.set(n2);
        metrics.put("krpc.server.queueFullErrorCount",n2);
        if( n2 > 0 ) {
            alarm.alarm(Alarm.ALARM_TYPE_QUEUEFULL,"krpc queue is full");
        }

        for (Object o : resources) {
            if (o == null) continue;
            if (o instanceof DumpPlugin) ((DumpPlugin) o).dump(metrics);
        }
        if( retryPool != null ) {
            metrics.put("krpc.client.retrypool.poolSize",retryPool.getPoolSize());
            metrics.put("krpc.client.retrypool.activeCount",retryPool.getActiveCount());
            metrics.put("krpc.client.retrypool.waitingInQueue",retryPool.getQueue().size());
        }
    }

    @Override
    public void healthCheck(List<HealthStatus> list) {

        int n = lastErrorCount.get();
        if( n > 0 ) {
            String alarmId = alarm.getAlarmId(Alarm.ALARM_TYPE_RPCSERVER);
            HealthStatus healthStatus = new HealthStatus(alarmId, false, "no krpc connection");
            list.add(healthStatus);
        }

        int n2 = lastQueueFullErrorCount.get();
        if( n2 > 0 ) {
            String alarmId = alarm.getAlarmId(Alarm.ALARM_TYPE_QUEUEFULL);
            HealthStatus healthStatus = new HealthStatus(alarmId, false, "krpc queue is full");
            list.add(healthStatus);
        }

        for (Object o : resources) {
            if (o == null) continue;
            if (o instanceof HealthPlugin) ((HealthPlugin) o).healthCheck(list);
        }
    }

    @Override
    public void setAlarm(Alarm alarm) {
        this.alarm = alarm;
    }

}
