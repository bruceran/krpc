package krpc.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import krpc.core.ClusterManager;
import krpc.core.InitCloseUtils;
import krpc.core.RpcClosure;

public class RpcClient extends RpcCallableBase {
	
	static Logger log = LoggerFactory.getLogger(RpcClient.class);

	ClusterManager clusterManager;
	
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
	
	String nextConnId(int serviceId,int msgId,Message req,String excludeConnIds) {
		return clusterManager.nextConnId(serviceId,msgId,req,excludeConnIds);
	}
	
	int nextSequence(String connId) {
		return connId == null ? 0 : clusterManager.nextSequence(connId);
	}

	public void connected(String connId,String localAddr) {
		clusterManager.connected(connId,localAddr);
		super.connected(connId, localAddr);
    }
	
	public void disconnected(String connId) {
		clusterManager.disconnected(connId);
		super.disconnected(connId);
    }
	
	public boolean isConnected(String connId) {
		return clusterManager.isConnected(connId);
    }
	
	void endCall(RpcClosure closure,Message res) {
		super.endCall(closure, res);
		clusterManager.updateStats(closure); // for loadbalance policy
	}

	public ClusterManager getClusterManager() {
		return clusterManager;
	}

	public void setClusterManager(ClusterManager clusterManager) {
		this.clusterManager = clusterManager;
	}

}
