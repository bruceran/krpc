package krpc.trace;

import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.Plugin;

public interface TraceAdapter extends Plugin {

	public String newTraceId();
	
	public String newStartServerRpcId(String traceId);
	public String newServerRpcId(String parentRpcId);

	public String newStartChildRpcId(String traceId);
	public String newChildRpcId(String parentRpcId,AtomicInteger subCalls);
	
	public void send(TraceContext ctx, Span span);
	
} 
