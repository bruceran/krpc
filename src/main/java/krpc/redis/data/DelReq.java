package krpc.redis.data;

import java.util.ArrayList;
import java.util.List;

public class DelReq extends BaseReq<DelReq> {
	
	List<String> keys = new ArrayList<>();

	public DelReq(String key, String ... keys) {
		if( isEmpty(key)  ) throw new IllegalArgumentException();
		add(key);
		for(String s:keys) {
			if( isEmpty(s)  ) throw new IllegalArgumentException();
			add(s);
		}
	}


	public String getCmd() {
		return "del";
	}		
	
	
	public Class<?> getResCls() {
		return DelRes.class;
	}
	
	
	public DelReq(List<String> keys) {
		if( keys == null || keys.size() == 0 ) throw new IllegalArgumentException();
		this.keys = keys;
	}
	
	public DelReq add(String key) {
		if( isEmpty(key) ) throw new IllegalArgumentException();
		keys.add(key);
		return this;
	}

	public List<String> getKeys() {
		return keys;
	}

	public RedisCommand toCommand() {
		RedisCommand c = super.toCommand();
		for(String s:keys) {
			c.add(s);
		}
		return c;
	}
}
