package krpc.cluster.lb;

import java.util.Random;

import com.google.protobuf.Message;

import krpc.cluster.Addr;
import krpc.cluster.LoadBalance;

public class RandomLoadBalance implements LoadBalance {
	Random rand = new Random();
	
	public int select(Addr[] addrs,int serviceId,int msgId,Message req) {
		return rand.nextInt(addrs.length);
	}
}