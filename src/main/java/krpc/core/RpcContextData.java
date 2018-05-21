package krpc.core;

import krpc.core.proto.RpcMeta;

abstract public class RpcContextData {
	
	protected String connId;
	RpcMeta meta;
	long startNanos = System.nanoTime();
	long timeUsed = 0; // nano time
	long responseTime = 0;
	
	public RpcContextData(String connId,RpcMeta meta) {
		this.connId = connId;
		this.meta = meta;
	}
	
	public long elapsedMillisByNow() {
		long ts = ( System.nanoTime() - startNanos ) / 1000000 ;
		return ts;
	}
	
	public long timeMicrosUsed() {
		return timeUsed / 1000 ;
	}
	
	public long timeMillisUsed() {
		return timeUsed / 1000000 ;
	}
	
	public void end() {
		timeUsed = System.nanoTime() - startNanos;
		responseTime = System.currentTimeMillis();
	}
	
	public String getClientIp() {
		String peers = meta.getPeers();
		if( peers.isEmpty() ) return getClientIp();
		return peers.split(",")[0];
	}
	
	public String getRemoteIp() {
		String remoteAddr = getRemoteAddr(); // to support ipv6
		int p = remoteAddr.lastIndexOf(":");
		return remoteAddr.substring(0,p);		
	}
	
	public String getRemoteAddr() {
		int p = connId.lastIndexOf(":");
		return connId.substring(0,p);		
	}
	
	public RpcMeta getMeta() {
		return meta;
	}
	public void setMeta(RpcMeta meta) {
		this.meta = meta;
	}
	public String getConnId() {
		return connId;
	}
	public void setConnId(String connId) {
		this.connId = connId;
	}

	public long getTimeUsed() {
		return timeUsed;
	}

	public long getResponseTime() {
		return responseTime;
	}
	
}
