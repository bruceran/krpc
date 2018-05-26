package krpc.rpc.web;

public interface HttpTransport {
    boolean send(String connId, DefaultWebRes data);
    void disconnect(String connId);
}

