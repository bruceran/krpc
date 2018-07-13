package krpc.rpc.core;

import com.google.protobuf.Message;

public interface ClusterManager extends RegistryManagerCallback {

    String nextConnId(ClientContextData ctx, Message req);

    int nextSequence(String connId);

    boolean isConnected(String connId);

    void connected(String connId, String localAddr);

    void disconnected(String connId);

    void updateStats(RpcClosure closure);
}
