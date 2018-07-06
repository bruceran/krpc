package krpc.test.misc;

import org.junit.Test;


import krpc.rpc.core.ServerContextData;
import krpc.rpc.core.proto.RpcMeta;
import krpc.rpc.impl.JedisFlowControl;
import krpc.rpc.impl.MemoryFlowControl;
import krpc.trace.DefaultTraceContext;
import krpc.trace.TraceContext;


public class FlowControlTest {

	@Test
	public void test1() throws Exception {
		
		System.out.println("memory testing ...");

		RpcMeta.Trace trace = RpcMeta.Trace.newBuilder().build();
		RpcMeta meta = RpcMeta.newBuilder().setServiceId(100).setMsgId(1).setTrace(trace).build();
		TraceContext traceContext = new DefaultTraceContext(trace,false);
		traceContext.startForServer("TEST","TEST");
		ServerContextData ctx = new ServerContextData("0:0:0",meta,traceContext);
		
		MemoryFlowControl impl = new MemoryFlowControl();
		impl.addLimit(100, 10, 5);
		
		for(int i=0;i<8;++i) {
			int failed = impl.preCall(ctx,null);
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
		
		RpcMeta.Trace trace = RpcMeta.Trace.newBuilder().build();
		RpcMeta meta = RpcMeta.newBuilder().setServiceId(100).setMsgId(1).setTrace(trace).build();
		TraceContext traceContext = new DefaultTraceContext(trace,false);
		traceContext.startForServer("TEST","TEST");
		ServerContextData ctx = new ServerContextData("0:0:0",meta,traceContext);
		
		
		for(int i=0;i<8;++i) {
			int failed = impl.preCall(ctx,null);
			System.out.println("failed="+failed);
			Thread.sleep(100);
		}
		
		impl.close();
	}
		
}

