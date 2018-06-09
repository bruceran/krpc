package krpc.rpc.cluster.lb;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;

public class RoundRobinLoadBalance implements LoadBalance {
	int index = -1;

	public int select(Addr[] addrs,int serviceId,int msgId,Message req) {
		index++;
		if( index >= addrs.length ) index = 0;
		return index;
	}
}