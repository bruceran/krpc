package krpc.redis.reqres;

public class DecrReq extends BaseKeyReq<DecrReq> {
	
	int decrement;
	
	public DecrReq(String key,int decrement) {
		this.key = key;
		this.decrement = decrement;
	}

	public int getDecrement() {
		return decrement;
	}

}
