package krpc.redis.data;

public class GetReq extends BaseKeyReq<GetReq> {

	public GetReq(String key) {
		if( isEmpty(key)  ) throw new IllegalArgumentException();
		this.key = key;
	}
	
	public String getCmd() {
		return "get";
	}
	
	public Class<?> getResCls() {
		return GetRes.class;
	}
		
}
