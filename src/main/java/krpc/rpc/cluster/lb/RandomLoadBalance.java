package krpc.rpc.cluster.lb;

import java.util.Random;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;

public class RandomLoadBalance implements LoadBalance {
	Random rand = new Random();
	
	public int select(Addr[] addrs,int serviceId,int msgId,Message req) {
		return rand.nextInt(addrs.length);
	}
}