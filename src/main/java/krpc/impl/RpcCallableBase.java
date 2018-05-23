package krpc.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import krpc.core.Transport;
import krpc.core.TransportCallback;
import krpc.core.proto.RpcMeta;
import krpc.core.DataManager;
import krpc.core.DataManagerCallback;
import krpc.core.ErrorMsgConverter;
import krpc.core.ExecutorManager;
import krpc.core.FlowControl;
import krpc.core.InitClose;
import krpc.core.InitCloseUtils;
import krpc.core.MockService;
import krpc.core.MonitorService;
import krpc.core.ReflectionUtils;
import krpc.core.TraceIdGenerator;
import krpc.core.RetCodes;
import krpc.core.RpcCallable;
import krpc.core.RpcClientContext;
import krpc.core.RpcClientContextData;
import krpc.core.RpcClosure;
import krpc.core.RpcServerContext;
import krpc.core.RpcServerContextData;
import krpc.core.RpcFutureFactory;
import krpc.core.RpcContextData;
import krpc.core.Continue;
import krpc.core.RpcData;
import krpc.core.RpcException;
import krpc.core.ServiceMetas;
import krpc.core.StartStop;
import krpc.util.NamedThreadFactory;

public abstract class RpcCallableBase implements TransportCallback, DataManagerCallback, RpcCallable, Continue<RpcClosure>, InitClose, StartStop {
	
	static Logger log = LoggerFactory.getLogger(RpcCallableBase.class);

	ServiceMetas serviceMetas;
	MonitorService monitorService;
	ErrorMsgConverter errorMsgConverter;
	FlowControl flowControl;
	TraceIdGenerator traceIdGenerator;
	
	// for both
	Transport transport;
	
	// for client functions: as a referer
	DataManager dataManager;  // must not be null
	RpcFutureFactory futureFactory; // must not be null
	int retryQueueSize = 1000;
	ThreadPoolExecutor retryPool;
	MockService mockService;
	
	// for server functions: as a service
	ExecutorManager executorManager;
	
	HashSet<Integer> allowedServices = new HashSet<Integer>();
	HashSet<Integer> allowedReferers = new HashSet<Integer>();
	HashMap<String,Integer> timeoutMap = new HashMap<String,Integer>();
	HashMap<String,Integer> retryLevelMap = new HashMap<String,Integer>();
	HashMap<String,Integer> retryCountMap = new HashMap<String,Integer>();

	ArrayList<Object> resources = new ArrayList<Object>();
	
	abstract boolean isServerSide();
	abstract String nextConnId(int serviceId,int msgId,Message req,String excludeConnIds);
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
	
	public void addRetryPolicy(int serviceId,int msgId,int timeout,int retryLevel,int retryCount) {
		String key = serviceId+":"+msgId;
		timeoutMap.put(key, timeout);
		retryLevelMap.put(key, retryLevel);
		retryCountMap.put(key, retryCount);
	}
	
	public void connected(String connId,String localAddr) {
    }
	
	public void disconnected(String connId) {
		if( dataManager != null)
			dataManager.disconnected(connId);
    }

	@SuppressWarnings("all")
	public Message call(int serviceId,int msgId,Message req) {
		int timeout = RpcClientContext.getTimeout();
		if( timeout <= 0 ) timeout = getTimeout(serviceId, msgId);

		try {
			CompletableFuture<Message> future = callAsyncInner(serviceId,msgId,req,false);
			return future.get(timeout,TimeUnit.MILLISECONDS);
		} catch( TimeoutException e) {
			return serviceMetas.generateRes(serviceId,msgId,RetCodes.RPC_TIMEOUT);
		} catch( InterruptedException e) {
			return serviceMetas.generateRes(serviceId,msgId,RetCodes.USER_CANCEL);
		} catch( Exception e) {
			return serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION);
		}
	}
	
	@SuppressWarnings("all")
	public CompletableFuture<Message> callAsync(int serviceId,int msgId,Message req)  {
		return callAsyncInner(serviceId,msgId,req,true);
	}
	
	@SuppressWarnings("all")
	private CompletableFuture<Message> callAsyncInner(int serviceId,int msgId,Message req,boolean isAsync)  {

		String connId = nextConnId(serviceId,msgId,req,null); // may be null
		int sequence = connId == null ? 0 : nextSequence(connId);
		RpcServerContextData svrCtx = RpcServerContext.get();
		RpcMeta.Builder builder = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST).setServiceId(serviceId).setMsgId(msgId).setSequence(sequence);
		builder.setTraceId(traceIdGenerator.nextTraceId(svrCtx));
		builder.setSpanId(traceIdGenerator.nextSpanId(svrCtx,false));
		if( svrCtx != null ) {
			builder.setPeers(svrCtx.getMeta().getPeers());
			builder.setAttachment(svrCtx.getMeta().getAttachment());
		} 
		
		String attachment = RpcClientContext.removeAttachment();
		if( attachment != null)
			builder.setAttachment(attachment);
		int timeout = RpcClientContext.removeTimeout();
		if( timeout > 0 )
			builder.setTimeout(timeout);
		else 
			builder.setTimeout(getTimeout(serviceId, msgId));
		
		RpcMeta meta = builder.build();
		RpcClientContextData ctx = new RpcClientContextData(connId == null ? "no_connection" : connId,meta);
		CompletableFuture<Message> future = futureFactory.newFuture(serviceId,msgId,isAsync);
		ctx.setFuture(future);
		RpcClientContext.set(ctx); // user code can call RpcClientContext.get() to obtain call information
		RpcClosure closure = new RpcClosure(ctx,req);
		
		if( !allowedReferers.contains(serviceId) ) {
			endCall(closure,RetCodes.REFERER_NOT_ALLOWED);
			return future;		
		}
		
		if( mockService != null ) { // mock response
			Message res = mockService.mock(serviceId, msgId, req);
			if( res != null ) {
				endCall(closure,res);
				return future;
			}
		}
		
		if( connId == null ) { // no connection, no need to retry
			endCall(closure,RetCodes.NO_CONNECTION);
			return future;
		}

		sendCall(closure,true);
		return future;
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
		if( retryCount == 0 || closure.asClientCtx().getRetryTimes() >= retryCount ) return false; // only support one retry
		String excludeConnIds = closure.asClientCtx().getRetriedConnIds();
		if( excludeConnIds == null ) excludeConnIds = closure.getCtx().getConnId();
		else excludeConnIds += "," + closure.getCtx().getConnId();
		final String newConnId = nextConnId(meta.getServiceId(),meta.getMsgId(),closure.getReq(),excludeConnIds); // may be null
		if( newConnId == null ) return false;
		
		try { 
			final String f_excludeConnIds = excludeConnIds;
			retryPool.execute( new Runnable() {
				public void run() {
					int newSequence = nextSequence(newConnId);
					String newSpanId = traceIdGenerator.nextSpanId(RpcServerContext.get(),false);
					RpcMeta newMeta = meta.toBuilder().setSequence(newSequence).setSpanId(newSpanId).build();
					closure.getCtx().setConnId(newConnId);
					closure.getCtx().setMeta(newMeta);
					closure.asClientCtx().incRetryTimes();
					closure.asClientCtx().setRetriedConnIds(f_excludeConnIds);
					
					sendCall(closure, closure.asClientCtx().getRetryTimes() < retryCount); // recursive call sendClosure
				}
			}) ;
	
			return true;
		} catch(Exception e) {
			log.error("rpcclient retry pool is full");
			return false;
		}
	}

	void endCall(RpcClosure closure,Message res) {
		closure.done(res);
		closure.asClientCtx().getFuture().complete(res);
		if( monitorService == null ) return;
		monitorService.callDone(closure);
	}
	
	void endCall(RpcClosure closure,int retCode) {
		RpcMeta meta = closure.getCtx().getMeta();
		Message res = serviceMetas.generateRes(meta.getServiceId(),meta.getMsgId(),retCode);
		endCall(closure,res);
	}
	
	public void timeout(RpcClosure closure) {
		
		int retryLevel = getRetryLevel(closure.getCtx().getMeta().getServiceId(),closure.getCtx().getMeta().getMsgId());
		if( retryLevel >= 4 ) {		
			if( retryCall(closure) ) return;
		}
		
		RpcMeta meta = closure.getCtx().getMeta();
		Message res = serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), RetCodes.RPC_TIMEOUT);
		endCall(closure,res);
	}
	public void disconnected(RpcClosure closure) {
	
		int retryLevel = getRetryLevel(closure.getCtx().getMeta().getServiceId(),closure.getCtx().getMeta().getMsgId());
		if( retryLevel >= 3 ) {		
			if( retryCall(closure) ) return;
		}
		
		RpcMeta meta = closure.getCtx().getMeta();
		Message res = serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), RetCodes.CONNECTION_BROKEN);
		endCall(closure,res);
	}

	public void receive(String connId,RpcData data) {

		if( isRequest(data.getMeta() )) {

			RpcServerContextData ctx = new RpcServerContextData(connId,data.getMeta());

			if( !allowedServices.contains(data.getMeta().getServiceId()) ) {
				sendErrorResponse(ctx,data.getBody(),RetCodes.SERVICE_NOT_ALLOWED);
				log.error("service id is not allowed, serviceId="+data.getMeta().getServiceId());
				return;
			}

			if( flowControl != null ) {
				if( !flowControl.isAsync() ) {
					boolean exceeded = flowControl.exceedLimit(data.getMeta().getServiceId(), data.getMeta().getMsgId(),null);
					if( exceeded ) {
			        	sendErrorResponse(ctx,data.getBody(),RetCodes.FLOW_LIMIT);
			        	return;
					}
				} else {
					flowControl.exceedLimit(data.getMeta().getServiceId(), data.getMeta().getMsgId(), new Continue<Boolean>() {
	    				public void readyToContinue(Boolean exceeded) {
	    					if( exceeded ) {
	    						sendErrorResponse(ctx,data.getBody(),RetCodes.FLOW_LIMIT);
	    	    	    		return;    			
	    	    	    	}
	    					continue1(ctx,data);
	    				}
	    			});
				}
			}
			
			continue1(ctx,data);
			
		} else {
			Message res = data.getBody();
			
			RpcClosure closure = dataManager.remove(connId, data.getMeta().getSequence());
			if( closure == null ) return; // data removed, ignore 
			
			int retryLevel = getRetryLevel(data.getMeta().getServiceId(),data.getMeta().getMsgId());
			int retCode = data.getMeta().getRetCode();
			if( RetCodes.canSafeRetry(retCode)) {
				if( retryCall(closure) ) return;
			}
			if( retryLevel == 4 && RetCodes.canRetryTimeout(retCode)) {
				if( retryCall(closure) ) return;
			}

			endCall(closure,res);			
		}
	}

	private void continue1(RpcServerContextData ctx,final RpcData data) {

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
	
	private void callServiceInPool(ThreadPoolExecutor pool, RpcServerContextData ctx,final RpcData data) {
		try {
			pool.execute(new Runnable() {
				public void run() {
					callService(ctx,data);
				}
			});
		} catch(Exception e) {
			sendErrorResponse(ctx,data.getBody(),RetCodes.QUEUE_FULL);
			log.error("queue is full");
			return;
		}
	}
	
	private void callService(RpcServerContextData ctx,RpcData data) {

		String connId = ctx.getConnId();
		
		if( !isConnected(connId) ) {
			RpcClosure closure = new RpcClosure(ctx,data.getBody());
			endReq(closure,RetCodes.SERVER_CONNECTION_BROKEN);
			return;	// connection is broken while waiting in runnable queue, just throw the request, no need to send response
		}
		
		long ts = ctx.elapsedMillisByNow();
		int clientTimeout = ctx.getMeta().getTimeout();
		if( clientTimeout > 0 && ts >= clientTimeout ) {
			sendErrorResponse(ctx,data.getBody(),RetCodes.QUEUE_TIMEOUT); // waiting too long, fast response with a TIMEOUT_EXPIRED
			return;
		}

		ctx.setContinue(this);
		RpcServerContext.set(ctx);

		try {  
			Message req = data.getBody();
			Message res = callService(ctx,req);
			if( res == null ) return; // an async service or exception, do nothing
			RpcClosure closure = new RpcClosure(ctx,req,res);
			readyToContinue(closure);
        } catch(Exception e) {  
        	sendErrorResponse(ctx,data.getBody(),RetCodes.BUSINESS_ERROR);
        	log.error("callService exception",e);
        	return;
        }   
	}
	
	private Message callService(RpcContextData ctx,Message req) throws Exception {
		RpcMeta meta = ctx.getMeta();

    	Object object = serviceMetas.findService(meta.getServiceId());
    	if( object == null ) {  
        	sendErrorResponse(ctx,req,RetCodes.NOT_FOUND);
        	return null;
    	}
    	Method method = serviceMetas.findMethod(meta.getServiceId(), meta.getMsgId());
    	if( method == null ) {  
        	sendErrorResponse(ctx,req,RetCodes.NOT_FOUND);
        	return null;
    	}

		Message res = (Message)method.invoke(object,new Object[]{req}); 
        return res;
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
		RpcMeta reqMeta = ctx.getMeta();
		RpcMeta resMeta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.RESPONSE).setServiceId(reqMeta.getServiceId()).setMsgId(reqMeta.getMsgId()).setSequence(reqMeta.getSequence()).setRetCode(retCode).build();
		transport.send(ctx.getConnId(), new RpcData(resMeta));
		
		RpcClosure closure = new RpcClosure(ctx,req);
		endReq(closure,retCode);
	}
	
	void endReq(RpcClosure closure,int retCode) {
		if( monitorService == null ) return;
		RpcMeta meta = closure.getCtx().getMeta();
		Message res = serviceMetas.generateRes(meta.getServiceId(),meta.getMsgId(),retCode);
		closure.done(res);
		monitorService.reqDone(closure);
	}

	void endReq(RpcClosure closure) {
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

	int getRetryLevel(int serviceId,int msgId) {
    	Integer retryLevel = retryLevelMap.get(serviceId+"."+msgId);
    	if( retryLevel == null ) retryLevel = retryLevelMap.get(serviceId+".-1");
    	if( retryLevel == null ) retryLevel = retryLevelMap.get("-1.-1");
    	return retryLevel == null ? 0 : retryLevel;		
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
	public FlowControl getFlowControl() {
		return flowControl;
	}
	public void setFlowControl(FlowControl flowControl) {
		this.flowControl = flowControl;
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
	public MockService getMockService() {
		return mockService;
	}
	public void setMockService(MockService mockService) {
		this.mockService = mockService;
	}
	public TraceIdGenerator getTraceIdGenerator() {
		return traceIdGenerator;
	}
	public void setTraceIdGenerator(TraceIdGenerator traceIdGenerator) {
		this.traceIdGenerator = traceIdGenerator;
	}
	
}
