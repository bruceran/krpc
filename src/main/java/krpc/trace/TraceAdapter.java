package krpc.trace;

import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.InitClose;

public interface TraceAdapter extends InitClose {

	public String newTraceId();
	public String newZeroRpcId(boolean isServer); // start point of rpcId
	public String newEntryRpcId(String parentRpcId);
	public String newChildRpcId(String parentRpcId,AtomicInteger subCalls);
	
	public void send(TraceContext ctx, Span span);
	
} 
