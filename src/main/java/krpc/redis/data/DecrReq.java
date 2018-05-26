package krpc.redis.data;

public class DecrReq extends BaseKeyReq<DecrReq> {
	
	int decrement;
	
	public DecrReq(String key) {
		if( isEmpty(key)  ) throw new IllegalArgumentException();
		this.key = key;	
	}

	public DecrReq(String key,int decrement) {
		if( isEmpty(key)  ) throw new IllegalArgumentException();
		this.key = key;
		this.decrement = decrement;	
	}

	public String getCmd() {
		return decrement == 0 ? "decr" : "decrby";
	}		
	
	public Class<?> getResCls() {
		return DecrRes.class;
	}
	
	public int getDecrement() {
		return decrement;
	}

	public RedisCommand toCommand() {
		RedisCommand c = super.toCommand();
		c.add(decrement);
		return c;
	}
	
}
