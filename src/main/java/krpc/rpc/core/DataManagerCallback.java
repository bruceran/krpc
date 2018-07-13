package krpc.rpc.core;

public interface DataManagerCallback {
    void timeout(RpcClosure closure);

    void disconnected(RpcClosure closure);
}
