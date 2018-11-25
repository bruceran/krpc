package krpc.rpc.core;

public interface MonitorService {
    void reqDone(RpcClosure closure); // req may be null, ctx/res not null

    void callDone(RpcClosure closure); // ctx/req/res not null

    void reportAlarm(String type,String msg); // alarm
}
