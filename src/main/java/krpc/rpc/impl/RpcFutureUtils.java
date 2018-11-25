package krpc.rpc.impl;

import com.google.protobuf.Message;
import krpc.common.RetCodes;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.core.ServerContext;
import krpc.rpc.core.ServerContextData;
import krpc.rpc.core.ServiceMetas;
import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class RpcFutureUtils  {

    private static Logger log = LoggerFactory.getLogger(RpcFutureUtils.class);

    public static ServiceMetas serviceMetas; // ugly, can be used by single RpcApp instance only

    private static RpcClosure createRpcClosure() {
        ServerContextData ctx = ServerContext.get();
        if( ctx != null ) {
            Message req = (Message)ctx.getAttribute("pendingReq");
            if( req != null ) {
                return new RpcClosure(ctx,req);
            }
        }
        return null;
    }

    private static Message createBizErrorMessage(RpcClosure closure) {
        if( serviceMetas == null ) return null;
        RpcMeta meta = closure.getCtx().getMeta();
        return serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), RetCodes.BUSINESS_ERROR);
    }

    public static Runnable wrap(Runnable runnable) {
        return new RunnableWrapper(runnable, createRpcClosure());
    }
    public static <T> Consumer<T> wrap(Consumer<T> action) {
        return new ConsumerWrapper<>(action, createRpcClosure());
    }
    public static <T1,T2> BiConsumer<T1,T2> wrap(BiConsumer<T1,T2> action) {
        return new BiConsumerWrapper<>(action, createRpcClosure());
    }
    public static <T,U> Function<T,U> wrap(Function<T,U> action) {
        return new FunctionWrapper<>(action, createRpcClosure());
    }
    public static <T1,T2,U> BiFunction<T1,T2,U> wrap(BiFunction<T1,T2,U> action) {
        return new BiFunctionWrapper<>(action, createRpcClosure());
    }

    private static class ConsumerWrapper<T> implements Consumer<T> {
        Consumer<T> action;
        RpcClosure closure;

        ConsumerWrapper(Consumer<T> action,RpcClosure closure) {
            this.action = action;
            this.closure = closure;
        }

        public void accept(T m) {
            try {
                if( closure != null ) {
                    closure.restoreContext();
                }
                action.accept(m);
            }  catch (Throwable e) {

                String traceId = "no_trace_id";
                if( Trace.currentContext() != null && Trace.currentContext().getTrace() != null )
                    traceId  = Trace.currentContext().getTrace().getTraceId();
                log.error("accept exception, traceId="+traceId, e);

                if( closure != null ) {
                    Message bizErrorMsg = createBizErrorMessage(closure);
                    if( bizErrorMsg != null ) {
                        closure.done(bizErrorMsg);
                    }
                }
                throw e;
            }
        }

    }

    private static class RunnableWrapper implements Runnable {

        Runnable runnable;
        RpcClosure closure;

        RunnableWrapper(Runnable runnable,RpcClosure closure) {
            this.runnable = runnable;
            this.closure = closure;
        }

        public void run() {
            try {
                if( closure != null ) {
                    closure.restoreContext();
                }

                runnable.run();

            }  catch (Throwable e) {

                String traceId = "no_trace_id";
                if( Trace.currentContext() != null && Trace.currentContext().getTrace() != null )
                    traceId  = Trace.currentContext().getTrace().getTraceId();
                log.error("run exception, traceId="+traceId, e);

                if( closure != null ) {
                    Message bizErrorMsg = createBizErrorMessage(closure);
                    if( bizErrorMsg != null ) {
                        closure.done(bizErrorMsg);
                    }
                }

                throw e;
            }
        }
    }

    private static class BiConsumerWrapper<T1,T2> implements BiConsumer<T1,T2> {

        BiConsumer<T1,T2> action;
        RpcClosure closure;

        BiConsumerWrapper(BiConsumer<T1,T2> action,RpcClosure closure) {
            this.action = action;
            this.closure = closure;
        }

        public void accept(T1 v1,T2 v2) {
            try {
                if( closure != null ) {
                    closure.restoreContext();
                }
                action.accept(v1,v2);
            }  catch (Throwable e) {

                String traceId = "no_trace_id";
                if( Trace.currentContext() != null && Trace.currentContext().getTrace() != null )
                    traceId  = Trace.currentContext().getTrace().getTraceId();
                log.error("accept exception, traceId="+traceId, e);

                if( closure != null ) {
                    Message bizErrorMsg = createBizErrorMessage(closure);
                    if( bizErrorMsg != null ) {
                        closure.done(bizErrorMsg);
                    }
                }
                throw e;
            }
        }
    }

    private static class FunctionWrapper<T,U> implements Function<T,U> {

        Function<T,U> f;
        RpcClosure closure;

        FunctionWrapper(Function<T,U> f,RpcClosure closure) {
            this.f = f;
            this.closure = closure;
        }
        public U apply(T m) {
            try {
                if( closure != null ) {
                    closure.restoreContext();
                }
                return f.apply(m);
            }  catch (Throwable e) {
                String traceId = "no_trace_id";
                if( Trace.currentContext() != null && Trace.currentContext().getTrace() != null )
                    traceId  = Trace.currentContext().getTrace().getTraceId();
                log.error("apply exception, traceId="+traceId, e);

                if( closure != null ) {
                    Message bizErrorMsg = createBizErrorMessage(closure);
                    if( bizErrorMsg != null ) {
                        closure.done(bizErrorMsg);
                    }
                }
                throw e;
            }
        }

    }


    private static class BiFunctionWrapper<T1,T2,U> implements BiFunction<T1,T2,U> {

        BiFunction<T1,T2,U> f;
        RpcClosure closure;

        BiFunctionWrapper(BiFunction<T1,T2,U> f, RpcClosure closure) {
            this.f = f;
            this.closure = closure;
        }
        public U apply(T1 v1,T2 v2) {
            try {
                if( closure != null ) {
                    closure.restoreContext();
                }
                return f.apply(v1,v2);
            }  catch (Throwable e) {
                String traceId = "no_trace_id";
                if( Trace.currentContext() != null && Trace.currentContext().getTrace() != null )
                    traceId  = Trace.currentContext().getTrace().getTraceId();
                log.error("apply exception, traceId="+traceId, e);

                if( closure != null ) {
                    Message bizErrorMsg = createBizErrorMessage(closure);
                    if( bizErrorMsg != null ) {
                        closure.done(bizErrorMsg);
                    }
                }
                throw e;
            }
        }

    }

}
