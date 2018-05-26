package krpc.redis.data;

public class BaseValueRes extends BaseRes {
	
	long value;

	public long getValue() {
		return value;
	}

	public void from(RedisResult res)  {
		super.from(res);
		if( res.retCode != 0 ) return;
		this.value = res.longValue;
	}

}
