package krpc.core;

public interface MonitorService {
    void reqDone(RpcClosure closure); // req can be null, ctx/res cannot be null
    void callDone(RpcClosure closure); // ctx/req/res cannot be null
}
