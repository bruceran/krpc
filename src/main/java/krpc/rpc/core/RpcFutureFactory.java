package krpc.rpc.core;

import com.google.protobuf.Message;
import krpc.trace.TraceContext;

import java.util.concurrent.CompletableFuture;

public interface RpcFutureFactory {
    CompletableFuture<Message> newFuture(int serviceId, int msgId, boolean isAsync, TraceContext traceContext);
}
