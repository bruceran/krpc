package krpc.redis;

import java.util.concurrent.CompletableFuture;

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
import krpc.redis.reqres.SetReq;
import krpc.redis.reqres.SetRes;

abstract public class AbstractRedisClient implements RedisClient   {

	protected abstract <T,R> R callSync(T req);
	protected abstract <T,R> CompletableFuture<R> callAsync(T req);
	
	public GetRes call(GetReq req) {
		return callSync(req);
	}

	public CompletableFuture<GetRes> callAsync(GetReq req) {
		return callAsync(req);
	}
	
	public SetRes call(SetReq req) {
		return callSync(req);
	}
	
	public CompletableFuture<SetRes> callAsync(SetReq req) {
		return callAsync(req);
	}
	
	public DelRes call(DelReq req) {
		return callSync(req);
	}
	
	public CompletableFuture<DelRes> callAsync(DelReq req) {
		return callAsync(req);
	}
	
	public IncrRes call(IncrReq req) {
		return callSync(req);
	}
	
	public CompletableFuture<IncrRes> callAsync(IncrReq req) {
		return callAsync(req);
	}
	
	public DecrRes call(DecrReq req) {
		return callSync(req);
	}
	
	public CompletableFuture<DecrRes> callAsync(DecrReq req) {
		return callAsync(req);
	}
	

	public HGetAllRes call(HGetAllReq req) {
		return callSync(req);
	}

	public CompletableFuture<HGetAllRes> callAsync(HGetAllReq req) {
		return callAsync(req);
	}
	
	
	public HMSetRes call(HMSetReq req) {
		return callSync(req);
	}
	
	public CompletableFuture<HMSetRes> callAsync(HMSetReq req) {
		return callAsync(req);
	}
 
	public BatchRes batch(BatchReq req) {
		return callSync(req);
	}

	public CompletableFuture<BatchRes> batchAsync(BatchReq req) {
		return callAsync(req);
	}

}
