package krpc.redis.impl;

import krpc.redis.reqres.RedisCommonReq;

public interface RedisTransport {

	public void connect(String connId,String addr);
	public void disconnect(String connId);

	public void send(String connId, RedisCommonReq creq);
	
}
