package krpc.trace;

import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.Plugin;

public interface TraceAdapter extends Plugin {
	
	static public class SpanIds {
		public String parentSpanId;
		public String spanId;
		SpanIds(String parentSpanId,String spanId) {
			this.parentSpanId = parentSpanId;
			this.spanId = spanId;
		}
	}
	
	public String newTraceId();
	public String newDefaultSpanId(boolean isServer,String traceId);
	public String newChildSpanId(String parentSpanId,AtomicInteger subCalls);
	public void convertRpcSpanIds(String traceId,SpanIds ids);
	
	public void send(TraceContext ctx, Span span);
	

} 
