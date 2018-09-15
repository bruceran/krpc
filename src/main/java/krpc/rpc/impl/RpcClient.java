package krpc.rpc.impl;

import com.google.protobuf.Message;
import krpc.common.InitCloseUtils;
import krpc.rpc.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcClient extends RpcCallableBase {

    static Logger log = LoggerFactory.getLogger(RpcClient.class);

    ClusterManager clusterManager;
    ConnectionPlugin connectionPlugin;

    public void init() {
        super.init();
        InitCloseUtils.init(clusterManager);
    }

    public void close() {
        super.close();
        InitCloseUtils.close(clusterManager);
    }

    boolean isServerSide() {
        return false;
    }

    String nextConnId(ClientContextData ctx, Message req) {
        String connId = ClientContext.removeConnId();
        if( connId != null ) return connId;
        return clusterManager.nextConnId(ctx, req);
    }

    int nextSequence(String connId) {
        return connId == null ? 0 : clusterManager.nextSequence(connId);
    }

    public void connected(String connId, String localAddr) {
        clusterManager.connected(connId, localAddr);
        super.connected(connId, localAddr);
        if( connectionPlugin != null ) {
            connectionPlugin.connected(connId,localAddr);
        }
    }

    public void disconnected(String connId) {
        clusterManager.disconnected(connId);
        super.disconnected(connId);
        if( connectionPlugin != null ) {
            connectionPlugin.disconnected(connId);
        }
    }

    public boolean isConnected(String connId) {
        return clusterManager.isConnected(connId);
    }

    void endCall(RpcClosure closure, Message res) {
        super.endCall(closure, res);
        clusterManager.updateStats(closure);
    }

    public ClusterManager getClusterManager() {
        return clusterManager;
    }

    public void setClusterManager(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }

    public ConnectionPlugin getConnectionPlugin() {
        return connectionPlugin;
    }

    public void setConnectionPlugin(ConnectionPlugin connectionPlugin) {
        this.connectionPlugin = connectionPlugin;
    }
}
