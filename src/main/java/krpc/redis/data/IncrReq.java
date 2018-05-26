package krpc.redis.data;

public class IncrReq extends BaseKeyReq<IncrReq> {
	
	int incrInt;
	float incrFloat;
	
	public IncrReq(String key) {
		if( isEmpty(key)  ) throw new IllegalArgumentException();
		this.key = key;
	}
	
	public IncrReq(String key,int increment) {
		if( isEmpty(key)  ) throw new IllegalArgumentException();
		this.key = key;
		this.incrInt = increment;
	}

	public IncrReq(String key,float increment) {
		if( isEmpty(key)  ) throw new IllegalArgumentException();
		this.key = key;
		this.incrFloat = increment;
	}
	

	public String getCmd() {
		return incrInt != 0 ? "incrby" : ( incrFloat != 0 ? "incrbyfloat" : "incr" ) ;
	}		
	
	
	public Class<?> getResCls() {
		return IncrRes.class;
	}
		
	
	public int getIncrInt() {
		return incrInt;
	}
	
	public float getIncrFloat() {
		return incrFloat;
	}

	public RedisCommand toCommand() {
		RedisCommand c = super.toCommand();
		if( incrInt != 0 )
			c.add(incrInt);
		else 
			c.add(incrFloat);
		return c;
	}
		
}
