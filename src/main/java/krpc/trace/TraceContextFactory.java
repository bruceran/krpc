package krpc.trace;

public interface TraceContextFactory {

	public TraceContext newTraceContext();
	public TraceContext newTraceContext(String traceId,String rpcId,String peers,String apps,int sampled,String type,String action);
} 
