package krpc.trace;

public class DefaultTraceContextFactory implements TraceContextFactory {

	public TraceContext newTraceContext() {
		return new DefaultTraceContext();
	}
	
	public TraceContext newTraceContext(String traceId,String rpcId,String peers,String apps,int sampled,String type,String action) {
		return new DefaultTraceContext(traceId,rpcId,peers,apps,sampled,type,action);
	}
} 
