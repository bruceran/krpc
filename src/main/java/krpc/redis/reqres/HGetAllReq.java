package krpc.redis.reqres;

public class HGetAllReq extends BaseKeyReq<HGetAllReq> {

	public HGetAllReq(String key) {
		this.key = key;
	}
	
}
