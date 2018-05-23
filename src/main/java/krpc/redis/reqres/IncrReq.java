package krpc.redis.reqres;

public class IncrReq extends BaseKeyReq<IncrReq> {
	
	int increment;
	
	public IncrReq(String key,int increment) {
		this.key = key;
		this.increment = increment;
	}

	public int getIncrement() {
		return increment;
	}

}
