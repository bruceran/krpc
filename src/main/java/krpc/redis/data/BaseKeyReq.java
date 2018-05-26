package krpc.redis.data;

abstract public class BaseKeyReq<T> extends BaseReq<T> {
	
	String key;

	public String getKey() {
		return key;
	}

	public RedisCommand toCommand() {
		RedisCommand c = super.toCommand();
		c.add(key);
		return c;
	}
}
