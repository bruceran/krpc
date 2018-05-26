package krpc.redis.data;

import java.util.HashMap;
import java.util.Map;

public class HMSetReq extends BaseKeyReq<HMSetReq> {
	
	Map<String,String> values = new HashMap<>();

	public HMSetReq(String key) {
		if( isEmpty(key) ) throw new IllegalArgumentException();
		this.key = key;
	}

	public String getCmd() {
		return "hmset";
	}	
	
	public Class<?> getResCls() {
		return HMSetRes.class;
	}	
	
	public HMSetReq add(String field,String value) {
		
		if( isEmpty(field) || isEmpty(value) ) throw new IllegalArgumentException();
		
		values.put(field, value);
		return this;
	}
	
	public Map<String, String> getValues() {
		return values;
	}

	public HMSetReq setValues(Map<String, String> values) {
		this.values.putAll(values);
		return this;
	}

	public RedisCommand toCommand() {
		
		if( values.size() == 0 ) throw new IllegalArgumentException();
		
		RedisCommand c = super.toCommand();
		for(Map.Entry<String,String> en : values.entrySet()) {
			c.add(en.getKey());
			c.add(en.getValue());
		}
		
		return c;
	}	
}
