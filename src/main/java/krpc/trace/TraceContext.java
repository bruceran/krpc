package krpc.trace;

public interface TraceContext {

	public void start(String type,String action);
	public Span startAsync(String type,String action);
	public Span currentSpan();
	
	public String getTraceId(); // received rpcId from network or generated
	public String getRootRpcId(); // received rpcId from network or generated
	public int getSampled();
	public long getRequestTimeMicros();
	public long getStartMicros();
	public String getRemoteAppName();
	
	public void stopped(Span span); // a span ended
	public void serverSpanStopped(String result); // the server span ended
	
	public long getThreadId();
	public String getThreadName();
	public String getThreadGroupName();
} 
