package krpc.redis.data;

public class BaseCountRes extends BaseRes {
	
	int count;

	public int count() {
		return count;
	}

	public void from(RedisResult res)  {
		super.from(res);
		if( res.retCode != 0 ) return;
		this.count = (int)res.longValue;
	}

}
