package krpc.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import krpc.core.InitClose;
import krpc.core.RpcFutureFactory;
import krpc.core.ServiceMetas;
import krpc.util.NamedThreadFactory;

public class DefaultRpcFutureFactory implements RpcFutureFactory,InitClose {
	
	static Logger log = LoggerFactory.getLogger(DefaultRpcFutureFactory.class);
	
	ServiceMetas serviceMetas;
	
	int notifyThreads = 1;
	int notifyMaxThreads = 0;
	int notifyQueueSize = 1000;
	ThreadPoolExecutor notifyPool;
	NamedThreadFactory threadFactory1 = new NamedThreadFactory("rpcnotify_thread");
	
	public void init() {
		log.info("notifyPool inited");
		if( notifyMaxThreads > notifyThreads ) 
			notifyPool = new ThreadPoolExecutor(notifyThreads, notifyMaxThreads, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(notifyQueueSize),threadFactory1);
		else
			notifyPool = new ThreadPoolExecutor(notifyThreads, notifyThreads, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(notifyQueueSize),threadFactory1);
        notifyPool.prestartAllCoreThreads();
	}
	
	public void close() {
		if( notifyPool != null)
			notifyPool.shutdown();
	}
	
	public CompletableFuture<Message> newFuture(int serviceId,int msgId,boolean isAsync) {
		return new NoExceptionCompletableFuture(this,serviceId,msgId,isAsync);
	}

	public int getNotifyThreads() {
		return notifyThreads;
	}

	public void setNotifyThreads(int notifyThreads) {
		this.notifyThreads = notifyThreads;
	}

	public int getNotifyQueueSize() {
		return notifyQueueSize;
	}

	public void setNotifyQueueSize(int notifyQueueSize) {
		this.notifyQueueSize = notifyQueueSize;
	}

	public ServiceMetas getServiceMetas() {
		return serviceMetas;
	}

	public void setServiceMetas(ServiceMetas serviceMetas) {
		this.serviceMetas = serviceMetas;
	}

	public int getNotifyMaxThreads() {
		return notifyMaxThreads;
	}

	public void setNotifyMaxThreads(int notifyMaxThreads) {
		this.notifyMaxThreads = notifyMaxThreads;
	}

	
}
