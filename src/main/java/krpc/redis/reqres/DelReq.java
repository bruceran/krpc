package krpc.redis.reqres;

import java.util.ArrayList;
import java.util.List;

public class DelReq extends BaseReq<DelReq> {
	
	List<String> keys;

	public DelReq(String key) {
		keys = new ArrayList<>();
		keys.add(key);
	}
	
	public DelReq(List<String> keys) {
		this.keys = keys;
	}
	
	public DelReq add(String key) {
		if( keys == null ) keys = new ArrayList<>();
		keys.add(key);
		return this;
	}

	public List<String> getKeys() {
		return keys;
	}

	public void setKeys(List<String> keys) {
		this.keys = keys;
	}

}
