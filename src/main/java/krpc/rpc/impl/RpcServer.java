package krpc.rpc.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import krpc.rpc.core.ClientContext;

public class RpcServer extends RpcCallableBase {
	
	static Logger log = LoggerFactory.getLogger(RpcServer.class);

	static class ConnInfo {
		String localAddr;
		AtomicInteger seq = new AtomicInteger(0);
		
		ConnInfo(String localAddr) { 
			this.localAddr = localAddr;
		}
	}
	
	ConcurrentHashMap<String,ConnInfo> clientConns = new ConcurrentHashMap<String,ConnInfo>();
	
	boolean isServerSide() {
		return true;
	}
	
	// user code must specify a connId in RpcContextClient to identify which client to call
	String nextConnId(int serviceId,int msgId,Message req,String excludeConnIds) {
		String connId = ClientContext.removeConnId();
		return connId;
	}

	int nextSequence(String connId) {
		ConnInfo ci = clientConns.get(connId);
		if( ci == null ) return 0;
		else return ci.seq.incrementAndGet();
	}

	public void connected(String connId,String localAddr) {
		super.connected(connId, localAddr);
		ConnInfo ci = new ConnInfo(localAddr);
		clientConns.put(connId, ci);
    }
	
	public void disconnected(String connId) {
		super.disconnected(connId);
		clientConns.remove(connId);
    }
	
	public boolean isConnected(String connId) {
		return clientConns.containsKey(connId);
    }

}
