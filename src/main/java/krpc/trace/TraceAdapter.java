package krpc.trace;

import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.Plugin;

public interface TraceAdapter extends Plugin {

	public String newTraceId();
	public String newZeroRpcId(boolean isServer); // start point of rpcId
	public String newEntryRpcId(String parentRpcId);
	public String newChildRpcId(String parentRpcId,AtomicInteger subCalls);
	
	public void send(TraceContext ctx, Span span);
	
} 
