package krpc.trace;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultTraceAdapter implements TraceAdapter {

	static Logger log = LoggerFactory.getLogger(DefaultTraceAdapter.class);

	public void send(TraceContext ctx, Span span) {
		// do nothing
	}
	
	public String newTraceId() {
		String s = UUID.randomUUID().toString();
	    return s.replaceAll("-", "");		
	}
	
	public String newZeroRpcId(boolean isServer) {
		if( isServer)
			return "0.1";
		else
			return "0";
	}
	
	public String newEntryRpcId(String parentRpcId) {
		return parentRpcId;
	}
	
	public String newChildRpcId(String parentRpcId,AtomicInteger subCalls) {
		return parentRpcId+"."+subCalls.incrementAndGet();  // 0.1.1.1
	}

} 
