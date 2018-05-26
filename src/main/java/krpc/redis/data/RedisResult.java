package krpc.redis.data;

import java.util.List;

public class RedisResult {
	
	int retCode;
	long longValue;
	String stringValue;
	List<RedisResult> arr;
	
	public int getRetCode() {
		return retCode;
	}
	public void setRetCode(int retCode) {
		this.retCode = retCode;
	}	

	public String getStringValue() {
		return stringValue;
	}
	public void setStringValue(String stringValue) {
		this.stringValue = stringValue;
	}
	public List<RedisResult> getArr() {
		return arr;
	}
	public void setArr(List<RedisResult> arr) {
		this.arr = arr;
	}
	public long getLongValue() {
		return longValue;
	}
	public void setLongValue(long longValue) {
		this.longValue = longValue;
	}


}
