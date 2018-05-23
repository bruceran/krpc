package krpc.redis.reqres;

import java.util.HashMap;
import java.util.Map;

public class HMSetReq extends BaseKeyReq<HMSetReq> {
	
	Map<String,String> values;

	public HMSetReq(String key) {
		this.key = key;
	}

	public HMSetReq add(String key,String value) {
		if( values == null ) values = new HashMap<>();
		values.put(key, value);
		return this;
	}
	
	public Map<String, String> getValues() {
		return values;
	}

	public HMSetReq setValues(Map<String, String> values) {
		this.values = values;
		return this;
	}

}
