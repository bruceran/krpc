package krpc.rpc.impl;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import krpc.common.InitCloseUtils;
import krpc.rpc.core.*;
import krpc.rpc.util.MessageToMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RpcClient extends RpcCallableBase {

    static Logger log = LoggerFactory.getLogger(RpcClient.class);

    ClusterManager clusterManager;
    List<ConnectionPlugin> connectionPlugins;
    RpcCodec rpcCodec;

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

    String nextConnId(ClientContextData ctx, Object req) {
        String connId = ClientContext.removeConnId();
        if( connId != null ) return connId;

        Map<String,Object> params = null;
        if( req != null && clusterManager.needReqInfoForNextConnId(ctx) ) {
            if( req instanceof Message ) {
                params = new HashMap<>();
                MessageToMap.parseMessage((Message)req, params, true, 0);
            } else if( rpcCodec != null ){
                ByteBuf bb = (ByteBuf)req;
                //int bak = bb.readerIndex();
                Message m = rpcCodec.decodeRawBody(ctx.getMeta(),bb);
                //bb.readerIndex(bak);
                if( m != null ) {
                    params = new HashMap<>();
                    MessageToMap.parseMessage((Message)req, params, true, 0);
                }
            }
        }
        return clusterManager.nextConnId(ctx, params);
    }

    int nextSequence(String connId) {
        return connId == null ? 0 : clusterManager.nextSequence(connId);
    }

    public void connected(String connId, String localAddr) {
        clusterManager.connected(connId, localAddr);
        super.connected(connId, localAddr);
        if( connectionPlugins != null ) {
            for( ConnectionPlugin c: connectionPlugins ) {
                c.connected(connId, localAddr);
            }
        }
    }

    public void disconnected(String connId) {
        clusterManager.disconnected(connId);
        super.disconnected(connId);
        if( connectionPlugins != null ) {
            for( ConnectionPlugin c: connectionPlugins ) {
                c.disconnected(connId);
            }
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

    public List<ConnectionPlugin> getConnectionPlugins() {
        return connectionPlugins;
    }

    public void addConnectionPlugin(ConnectionPlugin connectionPlugin) {
        if( connectionPlugins == null ) connectionPlugins = new ArrayList<>();
        connectionPlugins.add(connectionPlugin);
    }

    public RpcCodec getRpcCodec() {
        return rpcCodec;
    }

    public void setRpcCodec(RpcCodec rpcCodec) {
        this.rpcCodec = rpcCodec;
    }
}
