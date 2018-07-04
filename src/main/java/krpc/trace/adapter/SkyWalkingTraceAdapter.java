package krpc.trace.adapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.InitClose;
import krpc.common.Plugin;
import krpc.trace.Span;
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

	public String newTraceId() {
		String part2 = Long.toHexString(Thread.currentThread().getId());
		String part3 = Long.toHexString(t.nextLong());
		return applicationInstanceId + "." + part2 + "." +  part3; // same as newEntryRpcId but no suffix ':0'
	}
	
	public String newZeroRpcId(boolean isServer) {
		return newTraceId() + ":0";
	}
	
	public String newEntryRpcId(String parentRpcId) {
		return newTraceId() + ":0";
	}
	
	public String newChildRpcId(String parentRpcId,AtomicInteger subCalls) {
		int p = parentRpcId.indexOf(":");
		return parentRpcId.substring(0,p)+":"+subCalls.incrementAndGet(); // just increment span number
	}
	
	String uuid() {
		String s = UUID.randomUUID().toString();
	    return s.replaceAll("-", "");		
	}
	
} 
