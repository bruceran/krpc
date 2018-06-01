package krpc.redis;

import java.util.concurrent.CompletableFuture;

public interface RedisClient {

	public <T,R> R call(T req);
	public <T,R> CompletableFuture<R> callAsync(T req);

}
