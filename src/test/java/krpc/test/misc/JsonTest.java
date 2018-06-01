package krpc.test.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import krpc.common.Json;

public class JsonTest {

	@Test
	public void test1() throws Exception {
		Map<String,Object> map = new HashMap<String,Object>();
		map.put("retCode", -1001);
		map.put("retMsg", "error");
		String s = Json.toJson(map);
		System.out.println(s);
		Map<String,Object> map2 = Json.toMap(s);
		System.out.println(map2);
	}

	@Test
	public void test2() throws Exception {
		ArrayList<Object> list = new ArrayList<Object>();
		Map<String,Object> map = new HashMap<String,Object>();
		map.put("retCode", -1001);
		map.put("retMsg", "error");
		list.add(map);
		list.add("abc");
		String s = Json.toJson(list);
		System.out.println(s);
		List<Object> list2 = Json.toList(s);
		System.out.println(list2);
	}
	
}

