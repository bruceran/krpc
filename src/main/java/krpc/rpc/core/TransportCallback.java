package krpc.rpc.core;

import krpc.rpc.core.proto.RpcMeta;

public interface TransportCallback {

    boolean isExchange(String connId, RpcMeta meta);

    void receive(String connId, RpcData data, long receiveMicros);

    void connected(String connId, String localAddr);

    void disconnected(String connId);
}


