package krpc.redis.data;

abstract public class BaseReq<T> {

	int timeout;

	public int getTimeout() {
		return timeout;
	}

	public T setTimeout(int timeout) {
		this.timeout = timeout;
		return (T)this;
	}
	
	public String getKey() { return null; }
	
	public RedisCommand toCommand() {
		RedisCommand c = new RedisCommand();
		c.add(getCmd());
		return c;
	}

	abstract public String getCmd();
	abstract public Class<?> getResCls();

	boolean isEmpty(String v) {
		return v == null || v.isEmpty();
	}
}
