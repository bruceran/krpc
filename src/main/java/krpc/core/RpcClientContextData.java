package krpc.core;

import java.util.concurrent.CompletableFuture;

import com.google.protobuf.Message;

import krpc.core.proto.RpcMeta;

public class RpcClientContextData extends RpcContextData {
	
	CompletableFuture<Message> future;  // used in client side sync/async call
	int retryTimes = 0;
	String retriedConnIds = null; 
	
	public RpcClientContextData(String connId,RpcMeta meta) {
		super(connId,meta);
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
	
}
