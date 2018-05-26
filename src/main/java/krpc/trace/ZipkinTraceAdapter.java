package krpc.trace;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ZipkinTraceAdapter implements TraceAdapter {

	public ZipkinTraceAdapter(Map<String,String> params) {
	}
	
	public void init() { }
	public void close() {}
	
	public void send(TraceContext ctx, Span span) {
	}
	
	public String newTraceId() {
		String s = UUID.randomUUID().toString();
	    return s.replaceAll("-", "");		
	}
	
	public String newZeroRpcId(boolean isServer) {
		if( isServer )
			return "0:"+nextSpanId();
		else 
			return "0:0";
	}
	
	public String newEntryRpcId(String parentRpcId) {
		return parentRpcId;
	}
	
	public String newChildRpcId(String parentRpcId,AtomicInteger subCalls) {
		int p = parentRpcId.indexOf(":");
		return parentRpcId.substring(p+1)+":"+nextSpanId(); // parentSpanId : spanId
	}
	
	private String nextSpanId() {
		String s = UUID.randomUUID().toString();
	    s =  s.replaceAll("-", "");				
		return s.substring(0,16);
	}

} 
