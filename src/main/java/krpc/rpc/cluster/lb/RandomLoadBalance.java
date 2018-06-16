package krpc.rpc.cluster.lb;

import java.util.List;
import java.util.Random;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.core.ClientContextData;

public class RandomLoadBalance implements LoadBalance {
	Random rand = new Random();
	
	public int select(List<Addr> addrs,ClientContextData ctx,Message req) {
		return rand.nextInt(addrs.size());
	}
}