package krpc.redis;

import java.util.concurrent.CompletableFuture;

import krpc.redis.RedisClient;
import krpc.redis.reqres.*;


public interface RedisClient {

	public GetRes call(GetReq req);
	public CompletableFuture<GetRes> callAsync(GetReq req);
	public SetRes call(SetReq req);
	public CompletableFuture<SetRes> callAsync(SetReq req);

	public DelRes call(DelReq req);
	public CompletableFuture<DelRes> callAsync(DelReq req);
	public IncrRes call(IncrReq req);
	public CompletableFuture<IncrRes> callAsync(IncrReq req);
	public DecrRes call(DecrReq req);
	public CompletableFuture<DecrRes> callAsync(DecrReq req);
	
	public HGetAllRes call(HGetAllReq req);
	public CompletableFuture<HGetAllRes> callAsync(HGetAllReq req);
	public HMSetRes call(HMSetReq req);
	public CompletableFuture<HMSetRes> callAsync(HMSetReq req);
	public BatchRes batch(BatchReq req);
	public CompletableFuture<BatchRes> batchAsync(BatchReq req);

}
