package krpc.rpc.core;

public interface TransportCallback {
    void receive(String connId,RpcData data);
    void connected(String connId,String localAddr);
    void disconnected(String connId);
}


