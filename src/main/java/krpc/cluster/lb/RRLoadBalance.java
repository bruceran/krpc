package krpc.cluster.lb;

import com.google.protobuf.Message;

import krpc.cluster.Addr;
import krpc.cluster.LoadBalance;

public class RRLoadBalance implements LoadBalance {
	int index = -1;

	public int select(Addr[] addrs,int serviceId,int msgId,Message req) {
		index++;
		if( index >= addrs.length ) index = 0;
		return index;
	}
}