package krpc.trace.adapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.InitClose;
import krpc.common.Plugin;
import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.Span;
import krpc.trace.SpanIds;
import krpc.trace.TraceIds;
import krpc.trace.TraceAdapter;
import krpc.trace.TraceContext;

public class SkyWalkingTraceAdapter implements TraceAdapter,InitClose {

	private ThreadLocalRandom t = ThreadLocalRandom.current();
	
	private long applicationId = 0; // got from skywalking server
	private String applicationInstanceUuid = uuid();
	private long applicationInstanceId = 0; // got from skywalking server
	
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
	 
	}
	
	public void init() {}
	
	public void close() {}
	
	public void send(TraceContext ctx, Span span) {
		// todo
	}

	public TraceIds newStartTraceIds(boolean isServer) {
		String traceId = newTraceId();
		String s = newTraceId() + ":0";
		if( isServer ) return new TraceIds(traceId,"",s);
		else return new TraceIds(traceId,"",s);		
	}

	public SpanIds newChildSpanIds(String parentSpanId,AtomicInteger subCalls) {
		int p = parentSpanId.indexOf(":");
		String spanId = parentSpanId.substring(0,p)+":"+subCalls.incrementAndGet(); // just increment span number		
		return new SpanIds(parentSpanId,spanId);
	}

	public SpanIds restore(String parentSpanId, String spanId) {	
		String newParentSpanId = newTraceId() + ":0";
		String newSpanId = newTraceId() + ":0";		
		return new SpanIds(newParentSpanId,newSpanId);
	}	
	
	String newTraceId() {
		String part2 = Long.toHexString(Thread.currentThread().getId());
		String part3 = Long.toHexString(t.nextLong());
		return applicationInstanceId + "." + part2 + "." +  part3; // same as newEntryRpcId but no suffix ':0'
	}
	
	String uuid() {
		String s = UUID.randomUUID().toString();
	    return s.replaceAll("-", "");		
	}
	
} 
