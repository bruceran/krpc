package krpc.test.misc;

import org.junit.Test;
/*
import krpc.redis.RedisClient;
import krpc.redis.data.GetReq;
import krpc.redis.data.GetRes;
import krpc.redis.impl.RedisClientImpl;
*/
public class RedisTest {

	@Test
	public void test1() throws Exception {
		
		long requestTime = System.currentTimeMillis();
		System.out.println(requestTime);
		requestTime = System.currentTimeMillis()*1000;
		System.out.println(requestTime);
		requestTime = System.currentTimeMillis()*1000*1000;
		System.out.println(requestTime);
		//RedisClient c = new RedisClientImpl();
		//GetReq req = new GetReq("abc").setTimeout(3000);
		//GetRes res = c.call(req);
	}

}

