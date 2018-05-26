package krpc.rpc.core;

import com.google.protobuf.Message;

public interface ClusterManager extends RegistryManagerCallback {

	String nextConnId(int serviceId,int msgId,Message req,String excludeConnIds);
    int nextSequence(String connId);
    boolean isConnected(String connId);
    
    void connected(String connId,String localAddr);
    void disconnected(String connId);
    void updateStats(RpcClosure closure);
}
