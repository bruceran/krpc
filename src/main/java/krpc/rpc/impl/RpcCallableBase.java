package krpc.rpc.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import krpc.rpc.core.ClientContext;
import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.Continue;
import krpc.rpc.core.DataManager;
import krpc.rpc.core.DataManagerCallback;
import krpc.rpc.core.ErrorMsgConverter;
import krpc.rpc.core.ExecutorManager;
import krpc.rpc.core.FallbackPlugin;
import krpc.rpc.core.MonitorService;
import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.core.RpcCallable;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.RpcData;
import krpc.rpc.core.RpcException;
import krpc.rpc.core.RpcFutureFactory;
import krpc.rpc.core.RpcPlugin;
import krpc.rpc.core.ServerContext;
import krpc.rpc.core.ServerContextData;
import krpc.rpc.core.ServiceMetas;
import krpc.rpc.core.Transport;
import krpc.rpc.core.TransportCallback;
import krpc.rpc.core.Validator;
import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.TraceContext;
import krpc.trace.Span;
import krpc.trace.Trace;
import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.common.NamedThreadFactory;
import krpc.common.RetCodes;
import krpc.common.StartStop;

public abstract class RpcCallableBase implements TransportCallback, DataManagerCallback, RpcCallable, Continue<RpcClosure>, InitClose, StartStop {
	
	static Logger log = LoggerFactory.getLogger(RpcCallableBase.class);

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
	
	// for server functions: as a service
	ExecutorManager executorManager;
	Validator validator;
	FallbackPlugin fallbackPlugin;

	List<RpcPlugin> plugins = new ArrayList<>();

	HashSet<Integer> allowedServices = new HashSet<Integer>();
	HashSet<Integer> allowedReferers = new HashSet<Integer>();
	HashMap<String,Integer> timeoutMap = new HashMap<String,Integer>();
	HashMap<String,Integer> retryCountMap = new HashMap<String,Integer>();

	ArrayList<Object> resources = new ArrayList<Object>();

	abstract boolean isServerSide();
	abstract String nextConnId(ClientContextData ctx,Message req);
	abstract int nextSequence(String connId);
	abstract boolean isConnected(String connId);
	
	public boolean isRequest(RpcMeta meta) {
		return meta.getDirection() == RpcMeta.Direction.REQUEST;
	}
	
	public void init() {
		if(!isServerSide()) {
			NamedThreadFactory threadFactory2 = new NamedThreadFactory("rpcretry_thread");
	        retryPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(retryQueueSize),threadFactory2);
	        retryPool.prestartAllCoreThreads();
		}
		
		resources.add(transport);
		resources.add(dataManager);
		resources.add(futureFactory);
		resources.add(executorManager);
		
		for(RpcPlugin p:plugins) {
			resources.add(p);
		}

		InitCloseUtils.init(resources);
	}
	
	public void close() {
		InitCloseUtils.close(resources);

		if( retryPool != null)
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
	
	public void addRetryPolicy(int serviceId,int msgId,int timeout,int retryCount) {
		String key = serviceId+":"+msgId;
		if( timeoutMap.get(key) == null ) {
			timeoutMap.put(key, timeout);
			retryCountMap.put(key, retryCount);
		}
	}
	
	public void connected(String connId,String localAddr) {
    }
	
	public void disconnected(String connId) {
		if( dataManager != null)
			dataManager.disconnected(connId);
    }

	@SuppressWarnings("all")
	public Message call(int serviceId,int msgId,Message req) {
		int timeout = ClientContext.getTimeout();
		if( timeout <= 0 ) timeout = getTimeout(serviceId, msgId);

		try {
			CompletableFuture<Message> future = callAsyncInner(serviceId,msgId,req,false);
			return future.get(timeout,TimeUnit.MILLISECONDS);
		} catch( TimeoutException e) {
			return serviceMetas.generateRes(serviceId,msgId,RetCodes.RPC_TIMEOUT);
		} catch( InterruptedException e) {
			return serviceMetas.generateRes(serviceId,msgId,RetCodes.USER_CANCEL);
		} catch( Exception e) {
			log.error("exception",e);
			return serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION);
		}
	}
	
	@SuppressWarnings("all")
	public CompletableFuture<Message> callAsync(int serviceId,int msgId,Message req)  {
		return callAsyncInner(serviceId,msgId,req,true);
	}
	
	@SuppressWarnings("all")
	private CompletableFuture<Message> callAsyncInner(int serviceId,int msgId,Message req,boolean isAsync)  {

		RpcMeta.Builder builder = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST).setServiceId(serviceId).setMsgId(msgId);

		int timeout = ClientContext.removeTimeout();
		if( timeout > 0 )
			builder.setTimeout(timeout);
		else 
			builder.setTimeout(getTimeout(serviceId, msgId));
		
		String clientAttachment = ClientContext.removeAttachment();
		if( clientAttachment != null)
			builder.setAttachment(clientAttachment);

		String action = serviceMetas.getName(serviceId, msgId);
		Span span = Trace.startAsync("RPCCLIENT", action);
		TraceContext tctx = Trace.currentContext();
		
		String connId = "no_connection:0:0";
		span.setRemoteAddr(getAddr(connId));
		
		RpcMeta.Trace trace  = generateTraceInfo(tctx,span); 

		RpcMeta meta = builder.setTrace(trace).build();
		ClientContextData ctx = new ClientContextData("no_connection:0:0",meta, tctx, span);
		CompletableFuture<Message> future = futureFactory.newFuture(meta.getServiceId(),meta.getMsgId(),isAsync,ctx.getTraceContext());
		ctx.setFuture(future);
		ClientContext.set(ctx); // user code can call RpcClientContext.get() to obtain call information
		RpcClosure closure = new RpcClosure(ctx,req);

		if( !allowedReferers.contains(serviceId) ) {
			endCall(closure,RetCodes.REFERER_NOT_ALLOWED);
			return future;		
		}

		connId = nextConnId(ctx,req); // may be null
		if( connId == null ) { // no connection, no need to retry
			
			if( fallbackPlugin != null ) {
				Message res = fallbackPlugin.fallback(ctx, req);
				if( res != null ) {
					endCall(closure,res);
					return future;
				}
			}
			
			endCall(closure,RetCodes.NO_CONNECTION);
			return future;
		}

		ctx.setConnId(connId);
		int sequence = nextSequence(connId);
		ReflectionUtils.updateSequence(meta, sequence);
		span.setRemoteAddr(getAddr(connId));
		
		if( plugins.size() > 0 ) {
			List<RpcPlugin> calledPlugins = new ArrayList<>();
			ctx.setAttribute("calledClientPlugins", calledPlugins);
			for(RpcPlugin p:plugins) {
				int retCode = p.preCall(ctx, req);
				if( retCode != 0 ) {
					endCall(closure,retCode);
					return future;		
				}
				calledPlugins.add(p);
			}
		}
		
		sendCall(closure,true);
		return future;
	}
	
	private RpcMeta.Trace generateTraceInfo(TraceContext tctx,Span span) {
		RpcMeta.Trace.Builder traceBuilder = RpcMeta.Trace.newBuilder();
		Trace.inject(tctx, span,traceBuilder);
		traceBuilder.setPeers(tctx.getTrace().getPeers());
		traceBuilder.setSampleFlag(tctx.getTrace().getSampleFlag());
		return traceBuilder.build();
	}

	void sendCall(RpcClosure closure,boolean allowRetry) {
		RpcMeta meta = closure.getCtx().getMeta();
		try {
			dataManager.add(closure);
			
			RpcData data = new RpcData(meta,closure.getReq());
			boolean ok = transport.send(closure.getCtx().getConnId(),data);
			if( !ok ) { // can retry
				if( allowRetry ) {
					if( retryCall(closure) ) return;
				}
				dataManager.remove(closure);
				Message res = serviceMetas.generateRes(meta.getServiceId(),meta.getMsgId(),RetCodes.SEND_FAILED);
				endCall(closure,res);
			}
		} catch(RpcException e) {  // encode error, no need to support retry
			dataManager.remove(closure);
			endCall(closure,e.getRetCode());
		}
	}

	boolean retryCall(final RpcClosure closure) {
		
		if( isServerSide() ) return false;
		if( retryPool == null ) return false;
		
		final RpcMeta meta = closure.getCtx().getMeta();
		final int retryCount = getRetryCount(meta.getServiceId(),meta.getMsgId());
		if( retryCount == 0 || closure.asClientCtx().getRetryTimes() >= retryCount ) return false;
		closure.asClientCtx().incRetryTimes(closure.getCtx().getConnId());
		final String newConnId = nextConnId(closure.asClientCtx(),closure.getReq()); // may be null
		if( newConnId == null ) return false;
		
		try { 
			retryPool.execute( new Runnable() {
				public void run() {
					
					int newSequence = nextSequence(newConnId);
					RpcMeta newMeta = meta.toBuilder().setSequence(newSequence).build();
					closure.getCtx().setConnId(newConnId);
					closure.getCtx().setMeta(newMeta);
					closure.asClientCtx().getSpan().setRemoteAddr(getAddr(newConnId));
					
					sendCall(closure, closure.asClientCtx().getRetryTimes() < retryCount); // recursive call sendClosure
				}
			}) ;
	
			return true;
		} catch(Exception e) {
			log.error("rpcclient retry pool is full");
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	void endCall(RpcClosure closure,Message res) {
		
		List<RpcPlugin> calledPlugins = (List<RpcPlugin>)closure.getCtx().getAttribute("calledClientPlugins");
		if( calledPlugins != null ) {
			for(RpcPlugin p:calledPlugins) {
				 p.postCall(closure.getCtx(), closure.getReq(), closure.getRes());
			}
		}	
				
		closure.done(res);
		closure.asClientCtx().getFuture().complete(res);
		String status = closure.getRetCode() == 0 ? "SUCCESS" : "ERROR";
		closure.asClientCtx().getSpan().stop(status);
		if( monitorService != null ) {
			monitorService.callDone(closure);
		}
	}
	
	void endCall(RpcClosure closure,int retCode) {
		RpcMeta meta = closure.getCtx().getMeta();
		Message res = serviceMetas.generateRes(meta.getServiceId(),meta.getMsgId(),retCode);
		endCall(closure,res);
	}
	
	public void timeout(RpcClosure closure) {
		RpcMeta meta = closure.getCtx().getMeta();
		Message res = serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), RetCodes.RPC_TIMEOUT);
		endCall(closure,res);
	}
	public void disconnected(RpcClosure closure) {
		RpcMeta meta = closure.getCtx().getMeta();
		Message res = serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), RetCodes.CONNECTION_BROKEN);
		endCall(closure,res);
	}

	public String getAddr(String connId) {
		if( connId == null ) return "no_connection:0:0";
		int p = connId.lastIndexOf(":");
		if( p < 0 ) return connId;
		return connId.substring(0, p);
	}

	public void receive(String connId,RpcData data) {

		if( isRequest(data.getMeta() )) {

			RpcMeta meta = data.getMeta();
			String action = serviceMetas.getName(meta.getServiceId(), meta.getMsgId());
			Trace.startForServer(meta.getTrace(), "RPCSERVER", action );
			Trace.setRemoteAddr(getAddr(connId));
			ServerContextData ctx = new ServerContextData(connId,data.getMeta(),Trace.currentContext());
			ServerContext.set(ctx);
			
			if( !allowedServices.contains(data.getMeta().getServiceId()) ) {
				sendErrorResponse(ctx,data.getBody(),RetCodes.SERVICE_NOT_ALLOWED);
				log.error("service id is not allowed, serviceId="+data.getMeta().getServiceId());
				return;
			}

			continue1(ctx,data);
			
		} else {
			Message res = data.getBody();
			
			RpcClosure closure = dataManager.remove(connId, data.getMeta().getSequence());
			if( closure == null ) return; // data removed, ignore 
			
			int retCode = data.getMeta().getRetCode();
			if( RetCodes.canRetry(retCode)) {
				if( retryCall(closure) ) return;
			}

			endCall(closure,res);			
		}
	}

	private void continue1(ServerContextData ctx,final RpcData data) {

		// find a pool to execute the request
		if( executorManager != null ) {
			ThreadPoolExecutor pool = executorManager.getExecutor(data.getMeta().getServiceId(), data.getMeta().getMsgId());
			if( pool != null ) {
				callServiceInPool(pool, ctx, data);
				return;
			}				
		}
		callService(ctx, data);
		
	}
	
	private void callServiceInPool(ThreadPoolExecutor pool, ServerContextData ctx,final RpcData data) {
		try {
			pool.execute(new Runnable() {
				public void run() {
					ServerContext.set(ctx);
					
					callService(ctx,data);
				}
			});
		} catch(Exception e) {
			sendErrorResponse(ctx,data.getBody(),RetCodes.QUEUE_FULL);
			log.error("queue is full");
			return;
		}
	}
	
	private void callService(ServerContextData ctx,RpcData data) {

		String connId = ctx.getConnId();
		RpcMeta meta = ctx.getMeta();
		Message req = data.getBody();
		
		if( !isConnected(connId) ) {
			RpcClosure closure = new RpcClosure(ctx,req);
			endReq(closure,RetCodes.SERVER_CONNECTION_BROKEN);
			return;	// connection is broken while waiting in runnable queue, just throw the request, no need to send response
		}
		
		long ts = ctx.elapsedMillisByNow();
		int clientTimeout = ctx.getMeta().getTimeout();
		if( clientTimeout > 0 && ts >= clientTimeout ) {
			sendErrorResponse(ctx,req,RetCodes.QUEUE_TIMEOUT); // waiting too long, fast response with a TIMEOUT_EXPIRED
			return;
		}
		
    	Object object = serviceMetas.findService(meta.getServiceId());
    	if( object == null ) {  
        	sendErrorResponse(ctx,req,RetCodes.NOT_FOUND);
        	return;
    	}
    	Method method = serviceMetas.findMethod(meta.getServiceId(), meta.getMsgId());
    	if( method == null ) {  
        	sendErrorResponse(ctx,req,RetCodes.NOT_FOUND);
        	return;
    	}
    	
		ctx.setContinue(this);

		try {  

			if( plugins.size() > 0 ) {
				List<RpcPlugin> calledPlugins = new ArrayList<>();
				ctx.setAttribute("calledServerPlugins", calledPlugins);
				for(RpcPlugin p:plugins) {
					int retCode = p.preCall(ctx, req);
					if( retCode != 0 ) {
						sendErrorResponse(ctx,req,retCode);
						return;				
					}
					calledPlugins.add(p);
				}
			}
			
			if( validator != null ) {
				String result = validator.validate(req);
				if( result != null ) {
					String message = RetCodes.retCodeText(RetCodes.VALIDATE_ERROR) + result;
					sendErrorResponse(ctx, req, RetCodes.VALIDATE_ERROR,message);
					return;
				}
			}
			
			Message res = (Message)method.invoke(object,new Object[]{req}); 
			if( res == null ) return; // an async service or exception, do nothing
			
			RpcClosure closure = new RpcClosure(ctx,req,res);
			readyToContinue(closure);
        } catch(Exception e) {  
        	sendErrorResponse(ctx,data.getBody(),RetCodes.BUSINESS_ERROR);
        	log.error("callService exception",e);
        	Trace.logException(e);
        	return;
        }   
	}

	public void readyToContinue(RpcClosure closure) {
		String connId = closure.getCtx().getConnId();
		RpcMeta reqMeta = closure.getCtx().getMeta();
		int retCode = closure.getRetCode();
		if( retCode > 0 ) throw new RuntimeException("retCode>0 is not allowed");
		
		String retMsg = closure.getRetMsg();
		
    	if( retCode < 0 && (retMsg == null || retMsg.isEmpty() ) ) {
    		if( errorMsgConverter != null )
    			retMsg = errorMsgConverter.getErrorMsg(retCode);
    		if( retMsg != null && retMsg.length() > 0 ) {
    			ReflectionUtils.setRetMsg(closure.getRes(), retMsg);
    		}
    	}
    	
		RpcMeta resMeta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.RESPONSE).setServiceId(reqMeta.getServiceId()).setMsgId(reqMeta.getMsgId()).setSequence(reqMeta.getSequence()).setRetCode(retCode).build();
		transport.send(connId, new RpcData(resMeta,closure.getRes()));
		
		endReq(closure);
	}
	
	void sendErrorResponse(RpcContextData ctx, Message req, int retCode) {
		sendErrorResponse(ctx,req,retCode,null);
	}
		
	void sendErrorResponse(RpcContextData ctx, Message req, int retCode,String retMsg) {
		RpcMeta reqMeta = ctx.getMeta();
		RpcMeta resMeta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.RESPONSE).setServiceId(reqMeta.getServiceId()).setMsgId(reqMeta.getMsgId()).setSequence(reqMeta.getSequence()).setRetCode(retCode).build();
		RpcData data = null;
		if( retMsg != null ) {
			Message res = serviceMetas.generateRes(reqMeta.getServiceId(), reqMeta.getMsgId(), retCode, retMsg);
			data = new RpcData(resMeta,res);
		} else {
			data = new RpcData(resMeta);
		}
		transport.send(ctx.getConnId(), data);
		
		RpcClosure closure = new RpcClosure(ctx,req);
		endReq(closure,retCode);
	}
	
	@SuppressWarnings("unchecked")
	void endReq(RpcClosure closure,int retCode) {

		List<RpcPlugin> calledPlugins = (List<RpcPlugin>)closure.getCtx().getAttribute("calledServerPlugins");
		if( calledPlugins != null ) {
			for(RpcPlugin p:calledPlugins) {
				 p.postCall(closure.getCtx(), closure.getReq(), closure.getRes());
			}
		}	
		
		String status = retCode == 0 ? "SUCCESS" : "ERROR";
		closure.asServerCtx().getTraceContext().stopForServer(status);
		
		if( monitorService == null ) return;
		RpcMeta meta = closure.getCtx().getMeta();
		Message res = serviceMetas.generateRes(meta.getServiceId(),meta.getMsgId(),retCode);
		closure.done(res);
		monitorService.reqDone(closure);
	}

	void endReq(RpcClosure closure) {
		
		String status = closure.getRetCode() == 0 ? "SUCCESS" : "ERROR";
		closure.asServerCtx().getTraceContext().stopForServer(status);
		
		if( monitorService == null ) return;
		monitorService.reqDone(closure);
	}
	
	int getTimeout(int serviceId,int msgId) {
    	Integer timeout = timeoutMap.get(serviceId+"."+msgId);
    	if( timeout == null ) timeout = timeoutMap.get(serviceId+".-1");
    	if( timeout == null ) timeout = timeoutMap.get("-1.-1");
    	return timeout == null ? 3000 : timeout;		
	}
	
	int getRetryCount(int serviceId,int msgId) {
    	Integer retryCount = retryCountMap.get(serviceId+"."+msgId);
    	if( retryCount == null ) retryCount = retryCountMap.get(serviceId+".-1");
    	if( retryCount == null ) retryCount = retryCountMap.get("-1.-1");
    	return retryCount == null ? 0 : retryCount;		
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

}
