package krpc.rpc.core;

import java.util.concurrent.CompletableFuture;

import com.google.protobuf.Message;

public interface RpcCallable {
	
	Message call(int serviceId,int msgId,Message req);
	
	CompletableFuture<Message> callAsync(int serviceId,int msgId,Message req);
	
	ExecutorManager getExecutorManager();

}
