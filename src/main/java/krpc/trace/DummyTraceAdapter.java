package krpc.trace;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.trace.Span;
import krpc.trace.TraceAdapter;
import krpc.trace.TraceContext;

public class DummyTraceAdapter implements TraceAdapter {

	static Logger log = LoggerFactory.getLogger(DummyTraceAdapter.class);

	public void send(TraceContext ctx, Span span) {
		// do nothing
	}
	
	public String newTraceId() {
		String s = UUID.randomUUID().toString();
	    return s.replaceAll("-", "");		
	}

	public String newDefaultSpanId(boolean isServer,String traceId) {
		return isServer ? "0.1" : "0";
	}
	
	public void convertRpcSpanIds(String traceId,SpanIds ids) {	
	}
	
	public String newChildSpanId(String parentSpanId,AtomicInteger subCalls) {
		return parentSpanId+"."+subCalls.incrementAndGet();  // 0.1.1.1
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}
} 
