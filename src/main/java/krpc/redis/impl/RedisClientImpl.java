package krpc.redis.impl;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.redis.AbstractRedisClient;
import krpc.redis.RedisClient;
import krpc.redis.reqres.BaseReq;
import krpc.redis.reqres.BaseRes;
import krpc.redis.reqres.BatchReq;
import krpc.redis.reqres.BatchRes;
import krpc.redis.reqres.DecrReq;
import krpc.redis.reqres.DecrRes;
import krpc.redis.reqres.DelReq;
import krpc.redis.reqres.DelRes;
import krpc.redis.reqres.GetReq;
import krpc.redis.reqres.GetRes;
import krpc.redis.reqres.HGetAllReq;
import krpc.redis.reqres.HGetAllRes;
import krpc.redis.reqres.HMSetReq;
import krpc.redis.reqres.HMSetRes;
import krpc.redis.reqres.IncrReq;
import krpc.redis.reqres.IncrRes;
import krpc.redis.reqres.RedisCommonReq;
import krpc.redis.reqres.RedisCommonRes;
import krpc.redis.reqres.SetReq;
import krpc.redis.reqres.SetRes;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class RedisClientImpl extends AbstractRedisClient implements RedisTransportCallback  {

	static Logger log = LoggerFactory.getLogger(RedisClientImpl.class);

	int timeout = 500;
	RedisTransport transport;
	
	public void init() {
	}
	
	public void close() {
	}
	
	String selectConn(String key) {
		String connId = "";
		return connId;
	}
	
	static class PipelineData {
		int type;
		CompletableFuture future;
	}
	
	ConcurrentHashMap<String,Queue<PipelineData>> map;

	public void received(String connId, RedisCommonRes cres) {
		PipelineData ctx = null;
		BaseRes res = newResObj(ctx.type,0);
		res.from(cres);
		ctx.future.complete(res);
	}

	public <T,R> R callSync(T req){
		return (R)callSyncInner((BaseReq)req);
	}

	public <T,R> CompletableFuture<R> callAsync(T req) {
		return callAsyncInner((BaseReq)req,true);
	}

	CompletableFuture callAsyncInner(BaseReq req,boolean isAsync) {
		String connId = selectConn(req.getKey());
		RedisCommonReq creq = req.toReq();
		
		PipelineData ctx = new PipelineData();
		ctx.future = newCompletableFuture(true);
		ctx.type = req.getType();
		// save 
		
		transport.send(connId, creq);
		return ctx.future;
	}

	BaseRes callSyncInner(BaseReq<?> req) {
		int type = req.getType();
		int t = req.getTimeout() > 0 ? req.getTimeout() : timeout;
		try {
			CompletableFuture<BaseRes> future = callAsyncInner(req,false);
			return future.get(t,TimeUnit.MILLISECONDS);
		} catch( TimeoutException e) {
			return newResObj(type,-1);
		} catch( InterruptedException e) {
			return newResObj(type,-2);
		} catch( Exception e) {
			return newResObj(type,-3);
		}
	}

	BaseRes newResObj(int type,int retCode) {
		try {
			Class<BaseRes> cls = getResCls(type);
			BaseRes res = cls.newInstance();
			res.setRetCode(retCode);
			return res;
		} catch(Exception e) {
			return null;
		}			
	}
	
	Class<BaseRes> getResCls(int type) {
		return null;
	}

	CompletableFuture newCompletableFuture(boolean isAsync) {
		return new CompletableFuture<Object>();
	}

}
