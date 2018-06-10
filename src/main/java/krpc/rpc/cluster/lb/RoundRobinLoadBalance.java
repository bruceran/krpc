package krpc.rpc.cluster.lb;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;

public class RoundRobinLoadBalance implements LoadBalance {

	ConcurrentHashMap<Integer,AtomicInteger> map = new ConcurrentHashMap<>();
	
	public int select(Addr[] addrs,int serviceId,int msgId,Message req) {
		
		AtomicInteger ai = map.get(serviceId);
		if( ai == null ) {
			ai = new AtomicInteger(-1);
			map.put(serviceId, ai);
		}
		
		int index = ai.incrementAndGet();
		if( index >= 10000000 ) {
			ai.set(0);
		}

		return index % addrs.length ;
	}
}