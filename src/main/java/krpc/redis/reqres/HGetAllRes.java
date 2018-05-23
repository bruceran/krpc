package krpc.redis.reqres;

import java.util.Map;

public class HGetAllRes extends BaseRes {
	
	Map<String,String> values; // MUST not be null if retCode == 0
	
	public Map<String, String> getValues() {
		return values;
	}

	public void setValues(Map<String, String> values) {
		this.values = values;
	}
	
}
