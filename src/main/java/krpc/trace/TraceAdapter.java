package krpc.trace;

import krpc.common.Plugin;
import krpc.rpc.core.proto.RpcMeta;

import java.util.concurrent.atomic.AtomicInteger;

public interface TraceAdapter extends Plugin {

    default public TraceContext newTraceContext() {
        return new DefaultTraceContext();
    }

    default public TraceContext newTraceContext(RpcMeta.Trace trace, boolean restoreFlag) {
        return new DefaultTraceContext(trace, restoreFlag);
    }

    default public boolean needThreadInfo() {
        return false;
    } // only cat=true

    default public boolean useCtxSubCalls() {
        return false;
    } // only skywalking=true

    // inject rpc info except fields: peers,sampleFlag
    public void inject(TraceContext ctx, Span span, RpcMeta.Trace.Builder traceBuilder);

    // restore parentSpanId,spanId from rpc, no need to restore other fields
    // return "null" means no change, "not null" means changed
    public SpanIds restore(String parentSpanId, String spanId);

    public TraceIds newStartTraceIds(boolean isServerSide);

    public SpanIds newChildSpanIds(String spanId, AtomicInteger subCalls);

    public void send(TraceContext ctx, Span span);

} 
