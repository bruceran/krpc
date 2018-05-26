package krpc.redis.data;

public class BaseStringRes extends BaseRes {
	
	String value;

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public void from(RedisResult res)  {
		super.from(res);
		if( res.retCode != 0 ) return;
		this.value = res.stringValue;
	}
}
