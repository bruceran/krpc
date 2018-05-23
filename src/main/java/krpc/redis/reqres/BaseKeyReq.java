package krpc.redis.reqres;

public class BaseKeyReq<T> extends BaseReq<T> {
	
	String key;

	public String getKey() {
		return key;
	}

	@SuppressWarnings("unchecked")
	public T setKey(String key) {
		this.key = key;
		return (T)this;
	}
	
}
