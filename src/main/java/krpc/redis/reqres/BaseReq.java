package krpc.redis.reqres;

abstract public class BaseReq<T> {
	
	int type;
	
	int timeout;

	public int getTimeout() {
		return timeout;
	}

	public T setTimeout(int timeout) {
		this.timeout = timeout;
		return (T)this;
	}
	
	public String getKey() { return null; }
	public RedisCommonReq toReq() { return null; }

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}
}
