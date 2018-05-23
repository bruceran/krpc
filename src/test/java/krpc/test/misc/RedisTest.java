package krpc.test.misc;

import java.util.concurrent.CompletableFuture;

import org.junit.Test;

import krpc.redis.RedisClient;
import krpc.redis.impl.RedisClientImpl;
import krpc.redis.reqres.*;

public class RedisTest {

	@Test
	public void test1() throws Exception {
 
		RedisClient c = new RedisClientImpl();
		GetReq req = new GetReq("abc").setTimeout(3000);
		GetRes res = c.call(req);
		
	}

	
}

