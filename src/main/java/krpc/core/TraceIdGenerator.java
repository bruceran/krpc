package krpc.core;

public interface TraceIdGenerator {
    String nextId(RpcServerContextData data);
}


