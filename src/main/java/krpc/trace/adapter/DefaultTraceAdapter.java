package krpc.trace.adapter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.trace.DummyTraceAdapter;
import krpc.trace.Span;
import krpc.trace.SpanIds;
import krpc.trace.TraceIds;
import krpc.trace.TraceAdapter;
import krpc.trace.TraceContext;

public class DefaultTraceAdapter implements TraceAdapter {

	static Logger log = LoggerFactory.getLogger(DummyTraceAdapter.class);

	public void send(TraceContext ctx, Span span) {
		// do nothing
	}

	// like ali eagle style
	public TraceIds newStartTraceIds(boolean isServer) {
		String traceId = newTraceId();
		if( isServer ) return new TraceIds(traceId,"","0.1");
		else return new TraceIds(traceId,"","");
	}
	
	public SpanIds newChildSpanIds(String spanId,AtomicInteger subCalls) {
		if( isEmpty(spanId) ) spanId = "0";
		String childId = spanId+"."+subCalls.incrementAndGet();  // 0.1.1.1
		return new SpanIds("",childId);
	}

	String newTraceId() {
		String s = UUID.randomUUID().toString();
	    return s.replaceAll("-", "");		
	}
	
	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}

} 
