package krpc.redis.data;

import java.util.ArrayList;
import java.util.List;

public class BatchReq extends BaseReq<BatchReq> {

	List<BaseReq> reqs;
	
	public BatchReq add(BaseReq req) {
		if( reqs == null ) reqs = new ArrayList<>();
		reqs.add(req);
		return this;
	}
	
	public String getCmd() {
		return "batch"; // todo
	}	
	
	
	public Class<?> getResCls() {
		return BatchRes.class;
	}	
	
	
	public List<BaseReq> getReqs() {
		return reqs;
	}

	public void setReqs(List<BaseReq> reqs) {
		this.reqs = reqs;
	}
	
}
