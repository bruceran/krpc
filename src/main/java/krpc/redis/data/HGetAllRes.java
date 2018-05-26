package krpc.redis.data;

import java.util.HashMap;
import java.util.Map;

public class HGetAllRes extends BaseRes {
	
	Map<String,String> values; // MUST not be null if retCode == 0
	
	public Map<String, String> getValues() {
		return values;
	}

	public void from(RedisResult res)  {
		super.from(res);
		if( res.retCode != 0 ) return;
		values = new HashMap<>();
		if( res.arr == null ) return;
		
		for(int i=0;i<res.arr.size(); i+=2 ) {
			String key = res.arr.get(i).stringValue;
			String value = res.arr.get(i+1).stringValue;
			values.put(key, value);
		}
	}
	
}
