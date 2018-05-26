package krpc.trace;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// todo
public class CatTraceAdapter implements TraceAdapter {

	public CatTraceAdapter(Map<String,String> params) {
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
