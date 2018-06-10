package krpc.rpc.cluster.lb;

import java.util.Random;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;

public class RandomWeightLoadBalance implements LoadBalance {
	
	Random rand = new Random();
	
	public int select(Addr[] addrs,int serviceId,int msgId,Message req) {
		
		int[] weights =new int[addrs.length]; // weight may be changed during select
		for(int i=0;i<addrs.length;++i) {
			weights[i] = addrs[i].getWeight();
		}
		
		int total = 0;
		boolean same = true;

		for(int i=0;i<weights.length;++i) {
			total += weights[i];
			if( same && i >= 1 ) {
				if( weights[i] != weights[i-1] ) same = false;
			}
		}
		
		if( total == 0 || same ) {
			return rand.nextInt(addrs.length);
		}

		return select(weights,total);
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