package krpc.trace.adapter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.InitClose;
import krpc.common.Plugin;
import krpc.trace.Span;
import krpc.trace.TraceAdapter;
import krpc.trace.TraceContext;

// todo
public class CatTraceAdapter implements TraceAdapter,InitClose {

	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
	 
	}
	
	public void init() { }
	public void close() {}
	
	public void send(TraceContext ctx, Span span) {
	}
	
	public String newTraceId() {
		return "";	
	}
	
	public String newZeroRpcId(boolean isServer) {
		return "";
	}
	
	public String newEntryRpcId(String parentRpcId) {
		return "";
	}
	
	public String newChildRpcId(String parentRpcId,AtomicInteger subCalls) {
		return "";
	}
} 
