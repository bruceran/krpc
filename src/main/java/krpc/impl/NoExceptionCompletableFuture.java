package krpc.impl;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import krpc.core.RetCodes;

public class NoExceptionCompletableFuture extends CompletableFuture<Message> {
	
	static Logger log = LoggerFactory.getLogger(NoExceptionCompletableFuture.class);
	
	DefaultRpcFutureFactory factory;
	int serviceId;
	int msgId;
	boolean isAsync;
	
	NoExceptionCompletableFuture(DefaultRpcFutureFactory factory,int serviceId,int msgId,boolean isAsync) {
		this.factory = factory;
		this.serviceId = serviceId;
		this.msgId = msgId;
		this.isAsync = isAsync;
	}
	
	public boolean complete(Message m) {
		if( !isAsync ) return super.complete(m);
		try {
			factory.notifyPool.execute( new Runnable() {
				public void run() {
					NoExceptionCompletableFuture.super.complete(m);
				}
			});
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
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION);  // impossible
		} 
	}
	
	public Message getNow(Message valueIfAbsent) {
		try {
			return super.getNow(valueIfAbsent);
		} catch( CancellationException e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.USER_CANCEL);
		} catch( CompletionException  e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION);
		} 		
	}
	
	public Message join() {
		try {
			return super.join();
		} catch( CancellationException e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.USER_CANCEL);
		} catch( CompletionException  e) {
			return factory.serviceMetas.generateRes(serviceId,msgId,RetCodes.EXEC_EXCEPTION);
		} 	
	}
	
}
