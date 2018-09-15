package krpc.rpc.core;

import krpc.common.Plugin;

public interface ConnectionPlugin extends Plugin {

    void connected(String connId, String localAddr);
    void disconnected(String connId);

}
