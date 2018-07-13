package krpc.rpc.core;

import com.google.protobuf.Message;

import java.util.concurrent.CompletableFuture;

public interface RpcCallable {

    Message call(int serviceId, int msgId, Message req);

    CompletableFuture<Message> callAsync(int serviceId, int msgId, Message req);

    ExecutorManager getExecutorManager();

}
