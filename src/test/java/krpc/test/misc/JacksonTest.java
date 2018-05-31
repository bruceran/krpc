package krpc.test.misc;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import krpc.common.JacksonJsonConverter;

public class JacksonTest {

	@Test
	public void test1() throws Exception {
 
		JacksonJsonConverter c = new JacksonJsonConverter();
		Map<String,Object> map = new HashMap<String,Object>();
		map.put("retCode", -1001);
		map.put("retMsg", "error");
		String s = c.fromMap(map);
		System.out.println(s);
		Map<String,Object> map2 = c.toMap(s);
		System.out.println(map2);
	}

	
}

