package krpc.core;

public interface Transport {
    boolean send(String connId, RpcData data);
}

