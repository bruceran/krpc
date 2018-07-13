package krpc.rpc.core;

public interface TransportChannel {
    void connect(String connId, String addr);

    void disconnect(String connId);
}

