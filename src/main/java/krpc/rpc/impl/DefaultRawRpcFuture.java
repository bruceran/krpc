package krpc.rpc.impl;

import io.netty.buffer.Unpooled;
import krpc.common.RetCodes;
import krpc.rpc.core.RpcRawMessage;
import krpc.trace.Trace;
import krpc.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class DefaultRawRpcFuture extends CompletableFuture<RpcRawMessage> {

    static Logger log = LoggerFactory.getLogger(DefaultRawRpcFuture.class);

    DefaultRpcFutureFactory factory;
    int serviceId;
    int msgId;
    boolean isAsync;
    TraceContext tctx;

    DefaultRawRpcFuture(DefaultRpcFutureFactory factory, int serviceId, int msgId, boolean isAsync, TraceContext tctx) {
        this.factory = factory;
        this.serviceId = serviceId;
        this.msgId = msgId;
        this.isAsync = isAsync;
        this.tctx = tctx;
    }

    public boolean complete(RpcRawMessage m) {
        if (!isAsync) {
            return super.complete(m);
        }
        try {
            if (factory.notifyPool != null) {
                factory.notifyPool.execute(new Runnable() {
                    public void run() {
                        Trace.setCurrentContext(tctx);
                        DefaultRawRpcFuture.super.complete(m);
                    }
                });
            } else {
                Trace.setCurrentContext(tctx);
                DefaultRawRpcFuture.super.complete(m);
            }
            return true;
        } catch (Exception e) {
            log.error("queue is full for notify pool, e="+e.getMessage());
            return false;
        }
    }

    public RpcRawMessage get() throws InterruptedException, ExecutionException {

        try {
            return super.get();
        } catch (InterruptedException e) {
            return generateError(RetCodes.USER_CANCEL);
        } catch (Exception e) {
            log.error("exception", e);
            return generateError(RetCodes.EXEC_EXCEPTION); // impossible
        }
    }

    public RpcRawMessage get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {

        try {
            return super.get(timeout, unit);
        } catch (TimeoutException e) {
            return generateError(RetCodes.RPC_TIMEOUT);
        } catch (InterruptedException e) {
            return generateError(RetCodes.USER_CANCEL);
        } catch (Exception e) {
            log.error("exception", e);
            return generateError(RetCodes.EXEC_EXCEPTION);  // impossible
        }
    }

    public RpcRawMessage getNow(RpcRawMessage valueIfAbsent) {

        try {
            return super.getNow(valueIfAbsent);
        } catch (CancellationException e) {
            return generateError(RetCodes.USER_CANCEL);
        } catch (CompletionException e) {
            log.error("exception", e);
            return generateError(RetCodes.EXEC_EXCEPTION);
        }
    }

    public RpcRawMessage join() {

        try {
            return super.join();
        } catch (CancellationException e) {
            return generateError(RetCodes.USER_CANCEL);
        } catch (CompletionException e) {
            log.error("exception", e);
            return generateError(RetCodes.EXEC_EXCEPTION);
        }
    }

    RpcRawMessage generateError(int retCode) {
        return new RpcRawMessage(retCode,Unpooled.EMPTY_BUFFER);
    }

}
