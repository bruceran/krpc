package krpc.rpc.impl;

import com.google.protobuf.Message;
import krpc.rpc.core.ClientContext;
import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.ConnectionPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcServer extends RpcCallableBase {

    static Logger log = LoggerFactory.getLogger(RpcServer.class);

    static class ConnInfo {
        String localAddr;
        AtomicInteger seq = new AtomicInteger(0);

        ConnInfo(String localAddr) {
            this.localAddr = localAddr;
        }

        public int nextSequence() {
            int v = seq.incrementAndGet();
            if (v >= 10000000)
                seq.compareAndSet(v, 0);
            return v;
        }
    }

    ConcurrentHashMap<String, ConnInfo> clientConns = new ConcurrentHashMap<String, ConnInfo>();
    List<ConnectionPlugin> connectionPlugins;

    boolean isServerSide() {
        return true;
    }

    // user code must specify a connId in RpcContextClient to identify which client to call
    String nextConnId(ClientContextData ctx, Object dummy) {
        String connId = ClientContext.removeConnId();
        return connId;
    }

    int nextSequence(String connId) {
        ConnInfo ci = clientConns.get(connId);
        if (ci == null) return 0;
        else return ci.nextSequence();
    }

    public void connected(String connId, String localAddr) {
        super.connected(connId, localAddr);
        ConnInfo ci = new ConnInfo(localAddr);
        clientConns.put(connId, ci);
        if( connectionPlugins != null ) {
            for( ConnectionPlugin c: connectionPlugins ) {
                c.connected(connId, localAddr);
            }
        }
    }

    public void disconnected(String connId) {
        super.disconnected(connId);
        clientConns.remove(connId);
        if( connectionPlugins != null ) {
            for( ConnectionPlugin c: connectionPlugins ) {
                c.disconnected(connId);
            }
        }
    }

    public boolean isConnected(String connId) {
        return clientConns.containsKey(connId);
    }

    public List<ConnectionPlugin> getConnectionPlugins() {
        return connectionPlugins;
    }

    public void addConnectionPlugin(ConnectionPlugin connectionPlugin) {
        if( connectionPlugins == null ) connectionPlugins = new ArrayList<>();
        connectionPlugins.add(connectionPlugin);
    }
}
