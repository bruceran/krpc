package krpc.redis.reqres;

public class BaseValueRes extends BaseRes {
	
	long value;

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}

}
