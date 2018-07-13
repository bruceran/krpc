package krpc.rpc.web;

public interface HttpTransportCallback {
    void receive(String connId, DefaultWebReq data);

    void connected(String connId, String localAddr);

    void disconnected(String connId);
}
