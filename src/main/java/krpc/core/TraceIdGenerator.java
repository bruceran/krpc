package krpc.core;

public interface TraceIdGenerator {
	String nextTraceId(RpcServerContextData data);
    String nextSpanId(RpcServerContextData data,boolean isServer);
}


