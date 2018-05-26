package krpc.test.misc;

import org.junit.Test;

import krpc.rpc.impl.MemoryFlowControl;


public class FlowControlTest {

	@Test
	public void test1() throws Exception {
		
		MemoryFlowControl impl = new MemoryFlowControl();
		impl.addLimit(100, 3, 5);
		
		for(int i=0;i<8;++i) {
			boolean failed = impl.exceedLimit(100, 1,null);
			System.out.println("failed="+failed);
			Thread.sleep(100);
		}
		
	}
	
}

