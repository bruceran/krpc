package krpc.core;

import java.util.concurrent.CompletableFuture;

import com.google.protobuf.Message;

public interface RpcFutureFactory {
	CompletableFuture<Message> newFuture(int serviceId,int msgId,boolean isAsync);
}
