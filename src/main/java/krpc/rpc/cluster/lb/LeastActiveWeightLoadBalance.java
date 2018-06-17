package krpc.rpc.cluster.lb;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.cluster.Weights;
import krpc.rpc.core.ClientContextData;

public class LeastActiveWeightLoadBalance implements LoadBalance {

	Random rand = new Random();
	
	public int select(List<Addr> addrs,Weights weights, ClientContextData ctx,Message req) {
		
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

		int[] matchWeights =new int[match.size()]; // weight may be changed during select
		for(int i=0;i<match.size();++i) {
			matchWeights[i] = weights.getWeight(addrs.get(match.get(i)).getAddr() );
		}
		
		int total = 0;
		boolean same = true;

		for(int i=0;i<matchWeights.length;++i) {
			total += matchWeights[i];
			if( same && i >= 1 ) {
				if( matchWeights[i] != matchWeights[i-1] ) same = false;
			}
		}
		
		if( total == 0 || same ) {
			int r = rand.nextInt(match.size());
			return match.get(r);
		}

		int r = select(matchWeights,total);
		return match.get(r);
	}
	
	public int select(int[] weights,int total) {
		int offset = rand.nextInt(total);
		for(int i=0;i<weights.length;++i) {
			offset -= weights[i];
			if( offset < 0 ) return i;
		}
		
		return 0; // impossible
	}
	
}
