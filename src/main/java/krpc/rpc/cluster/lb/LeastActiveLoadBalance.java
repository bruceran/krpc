package krpc.rpc.cluster.lb;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.core.ClientContextData;

public class LeastActiveLoadBalance implements LoadBalance {

	Random rand = new Random();
	
	public int select(List<Addr> addrs,ClientContextData ctx,Message req) {
		
		int min = Integer.MAX_VALUE;
		
		int[] pendings =new int[addrs.size()]; // pending may be changed during select
		for(int i=0;i<pendings.length;++i) {
			pendings[i] = addrs.get(i).getPendingCalls();
			if( pendings[i] < min ) min = pendings[i] ;
		}
		
		List<Integer> match = new ArrayList<>(pendings.length);
		for(int i=0;i<pendings.length;++i) {
			if( pendings[i] == min ) match.add(i);
		}
		
		if( match.size() == 1 ) return match.get(0);
		
		int  r = rand.nextInt(match.size());
		return match.get(r);
	}
}
