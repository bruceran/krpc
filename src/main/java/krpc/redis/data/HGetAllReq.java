package krpc.redis.data;

public class HGetAllReq extends BaseKeyReq<HGetAllReq> {

	public HGetAllReq(String key) {
		if( isEmpty(key)  ) throw new IllegalArgumentException();
		this.key = key;
	}
	
	public String getCmd() {
		return "hgetall";
	}	
	
	
	public Class<?> getResCls() {
		return HGetAllRes.class;
	}
		
	
}
