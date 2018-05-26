package krpc.rpc.core;

import java.util.concurrent.CompletableFuture;

import com.google.protobuf.Message;

import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.Span;
import krpc.trace.TraceContext;

public class ClientContextData extends RpcContextData {
	
	CompletableFuture<Message> future;  // used in client side sync/async call
	int retryTimes = 0;
	String retriedConnIds = null; 
	TraceContext traceContext;
	Span span;
	
	public ClientContextData(String connId,RpcMeta meta,TraceContext traceContext,Span span) {
		super(connId,meta);
		this.traceContext = traceContext;
		this.span = span;
		startMicros = span.getStartMicros();			
		requestTimeMicros = traceContext.getRequestTimeMicros() + ( startMicros - traceContext.getStartMicros() );				
	}
	
	public void incRetryTimes() {
		retryTimes++;
	}

	public CompletableFuture<Message> getFuture() {
		return future;
	}

	public void setFuture(CompletableFuture<Message> future) {
		this.future = future;
	}

	public int getRetryTimes() {
		return retryTimes;
	}

	public void setRetryTimes(int retryTimes) {
		this.retryTimes = retryTimes;
	}

	public String getRetriedConnIds() {
		return retriedConnIds;
	}

	public void setRetriedConnIds(String retriedConnIds) {
		this.retriedConnIds = retriedConnIds;
	}

	public TraceContext getTraceContext() {
		return traceContext;
	}

	public Span getSpan() {
		return span;
	}
	
}
