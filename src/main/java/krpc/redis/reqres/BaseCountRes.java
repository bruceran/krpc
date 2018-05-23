package krpc.redis.reqres;

public class BaseCountRes extends BaseRes {
	
	int count;

	public int count() {
		return count;
	}

	public void count(int count) {
		this.count = count;
	}

}
