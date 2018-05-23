package krpc.redis.reqres;

abstract public class BaseRes {
	
	int retCode;

	public int getRetCode() {
		return retCode;
	}

	public void setRetCode(int retCode) {
		this.retCode = retCode;
	}
	
	public void from(RedisCommonRes res)  {  }
}
