package krpc.rpc.core;

import java.util.Map;

public interface ClusterManager extends RegistryManagerCallback {

    boolean needReqInfoForNextConnId(ClientContextData ctx);

    String nextConnId(ClientContextData ctx, Map<String,Object> req);

    int nextSequence(String connId);

    boolean isConnected(String connId);

    void connected(String connId, String localAddr);

    void disconnected(String connId);

    void updateStats(RpcClosure closure);
}
