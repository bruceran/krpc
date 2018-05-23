package krpc.redis.reqres;

import java.util.List;

public class RedisCommonRes {
	
	boolean ok;
	int intValue;
	String stringValue;
	List<RedisCommonRes> arr;
	
	public boolean isOk() {
		return ok;
	}
	public void setOk(boolean ok) {
		this.ok = ok;
	}
	public int getIntValue() {
		return intValue;
	}
	public void setIntValue(int intValue) {
		this.intValue = intValue;
	}
	public String getStringValue() {
		return stringValue;
	}
	public void setStringValue(String stringValue) {
		this.stringValue = stringValue;
	}
	public List<RedisCommonRes> getArr() {
		return arr;
	}
	public void setArr(List<RedisCommonRes> arr) {
		this.arr = arr;
	}

}
