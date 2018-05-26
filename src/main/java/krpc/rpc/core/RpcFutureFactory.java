package krpc.rpc.core;

import java.util.concurrent.CompletableFuture;

import com.google.protobuf.Message;

import krpc.trace.TraceContext;

public interface RpcFutureFactory {
	CompletableFuture<Message> newFuture(int serviceId,int msgId,boolean isAsync,TraceContext traceContext);
}
