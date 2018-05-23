package krpc.redis.impl;

import krpc.redis.reqres.RedisCommonRes;

public interface RedisTransportCallback {

	public void received(String connId, RedisCommonRes cres);

}
