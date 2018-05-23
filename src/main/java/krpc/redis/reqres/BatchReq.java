package krpc.redis.reqres;

import java.util.ArrayList;
import java.util.List;

public class BatchReq extends BaseReq<BatchReq> {

	List<BaseReq> reqs;
	
	public BatchReq add(BaseReq req) {
		if( reqs == null ) reqs = new ArrayList<>();
		reqs.add(req);
		return this;
	}

	public List<BaseReq> getReqs() {
		return reqs;
	}

	public void setReqs(List<BaseReq> reqs) {
		this.reqs = reqs;
	}
	
}
