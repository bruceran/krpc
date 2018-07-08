package krpc.rpc.impl;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import krpc.common.RetCodes;
import krpc.trace.Trace;
import krpc.trace.TraceContext;

public class DefaultRpcFuture extends CompletableFuture<Message> {
	
	static Logger log = LoggerFactory.getLogger(DefaultRpcFuture.class);
	
	DefaultRpcFutureFactory factory;
	int serviceId;
	int msgId;
	boolean isAsync;
	TraceContext traceContext;
	
	DefaultRpcFuture(DefaultRpcFutureFactory factory,int serviceId,int msgId,boolean isAsync,TraceContext traceContext) {
		this.factory = factory;
		this.serviceId = serviceId;
		this.msgId = msgId;
		this.isAsync = isAsync;
		if( isAsync ) {
			this.traceContext = traceContext;
		}
	}
	
	public boolean complete(Message m) {
		if( !isAsync ) {
			return super.complete(m);
		}
		try {
			
			if( factory.notifyPool != null ) {
				factory.notifyPool.execute( new Runnable() {
					public void run() {
						Trace.setCurrentContext(DefaultRpcFuture.this.traceContext);
						DefaultRpcFuture.super.complete(m);
					}
				});
			} else {
				Trace.setCurrentContext(DefaultRpcFuture.this.traceContext);
				DefaultRpcFuture.super.complete(m);
			}
			return true;
		} catch(Exception e) {
			log.error("queue is full for notify pool");
			return false;
		}
	}
	
	public Message get() throws InterruptedException, ExecutionException {

		try {
			return super.get();
		} catch( InterruptedException e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.USER_CANCEL);
		} catch( Exception e) {
			log.error("exception",e);
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION); // impossible
		}
	}
	
	public Message get(long timeout, TimeUnit unit)  throws InterruptedException, ExecutionException, TimeoutException {

		try {
			return super.get(timeout,unit);
		} catch( TimeoutException e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.RPC_TIMEOUT);
		} catch( InterruptedException e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.USER_CANCEL);
		} catch( Exception e) {
			log.error("exception",e);
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION);  // impossible
		} 
	}
	
	public Message getNow(Message valueIfAbsent) {

		try {
			return super.getNow(valueIfAbsent);
		} catch( CancellationException e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.USER_CANCEL);
		} catch( CompletionException  e) {
			log.error("exception",e);
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION);
		} 		
	}
	
	public Message join() {

		try {
			return super.join();
		} catch( CancellationException e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.USER_CANCEL);
		} catch( CompletionException  e) {
			log.error("exception",e);
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION);
		} 	
	}
	
}
