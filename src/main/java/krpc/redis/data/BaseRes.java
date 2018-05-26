package krpc.redis.data;

abstract public class BaseRes {
	
	int retCode;

	public int getRetCode() {
		return retCode;
	}

	public void from(RedisResult res)  {
		this.retCode = res.retCode;
	}

	public void setRetCode(int retCode) {
		this.retCode = retCode;
	}
}
