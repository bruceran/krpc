package krpc.trace;

import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.Plugin;
import krpc.rpc.core.proto.RpcMeta;

public interface TraceAdapter extends Plugin {
	
	default public TraceContext newTraceContext() { return new DefaultTraceContext(); } 
	
	default public TraceContext newTraceContext(RpcMeta.Trace trace,boolean restoreFlag) { return new DefaultTraceContext(trace,restoreFlag); } 

	default public boolean needAppNames() { return false;  }

	// inject for rpc
	default public TraceIds inject(TraceContext ctx, Span span) {
		return new TraceIds(ctx.getTrace().getTraceId(),span.getParentSpanId(),span.getSpanId());
	}
	
	// restore from rpc, no need to restore traceId
	// parentSpanId or spanId may be changed
	// return "null" means no change, "not null" means changed
	default public SpanIds restore(String parentSpanId, String spanId) {
		return null;
	}
	
	public TraceIds newStartTraceIds(boolean isServerSide);
	
	public SpanIds newChildSpanIds(String spanId,AtomicInteger subCalls);
	
	public void send(TraceContext ctx, Span span);

} 
