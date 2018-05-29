package krpc.test.misc;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import krpc.rpc.web.impl.JedisSessionService;

public class RedisTest {

	//@Test
	public void test1() throws Exception {
		
		JedisSessionService s = new JedisSessionService();
		s.config("addrs=127.0.0.1:6379");
		s.init();
		
		Map<String,String> values  = new HashMap<>();
		values.put("a", "1");
		values.put("b", "2");
		String sessionId = "12345";
		s.update(sessionId,values,null);

		Map<String,String> values2  = new HashMap<>();
		values2.put("c", "3");
		s.update(sessionId,values2,null);
		
		for(int i=0;i<100;++i) {
			String key = "a"+i;
			Map<String,String> vv  = new HashMap<>();
			vv.put("mm", "3");
			s.update(key,vv,null);			
		}
		
		Map<String,String> v = new HashMap<>();
		s.load(sessionId, v, null);
		System.out.println(v);
		
		s.remove(sessionId, null);
		
		Map<String,String> v2 = new HashMap<>();
		s.load(sessionId, v2, null);
		System.out.println(v2);		
	}


}

