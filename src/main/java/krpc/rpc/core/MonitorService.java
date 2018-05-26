package krpc.rpc.core;

public interface MonitorService {
    void reqStart(RpcClosure closure); // req may be null, ctx not null, res is null
    void reqDone(RpcClosure closure); // req may be null, ctx/res not null

    void callStart(RpcClosure closure); // ctx/req not null, res is null
    void callDone(RpcClosure closure); // ctx/req/res not null
}
