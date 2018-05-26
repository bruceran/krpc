package krpc.rpc.core;

public interface Transport {
    boolean send(String connId, RpcData data);
}

