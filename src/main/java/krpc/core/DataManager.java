package krpc.core;

public interface DataManager {
    void add(RpcClosure closure);
    void remove(RpcClosure closure);
    RpcClosure remove(String connId,int sequence);
    void disconnected(String connId);
}
