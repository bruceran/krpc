package krpc.test.misc;

import org.junit.Test;

import krpc.rpc.impl.JedisFlowControl;
import krpc.rpc.impl.MemoryFlowControl;


public class FlowControlTest {

	@Test
	public void test1() throws Exception {
		
		System.out.println("memory testing ...");

		MemoryFlowControl impl = new MemoryFlowControl();
		impl.addLimit(100, 10, 5);
		
		for(int i=0;i<8;++i) {
			boolean failed = impl.exceedLimit(100, 1,null);
			System.out.println("failed="+failed);
			Thread.sleep(100);
		}
		
	}
	
	public void test2() throws Exception {
		
		System.out.println("jedis testing ...");
		
		JedisFlowControl impl = new JedisFlowControl();
		impl.config("syncUpdate=true;addrs=127.0.0.1:6379");
		impl.addLimit(100, 10, 5);
		impl.init();
		
		for(int i=0;i<8;++i) {
			boolean failed = impl.exceedLimit(100, 1,null);
			System.out.println("failed="+failed);
			Thread.sleep(100);
		}
		
		impl.close();
	}
		
}

