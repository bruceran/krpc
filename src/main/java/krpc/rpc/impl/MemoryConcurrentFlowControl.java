package krpc.rpc.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.Message;

import krpc.common.Plugin;
import krpc.common.RetCodes;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.RpcPlugin;

public class MemoryConcurrentFlowControl extends AbstractConcurrentFlowControl implements RpcPlugin {
	
	static class StatItem {
		int limit;
		AtomicInteger curValue = new AtomicInteger();
		
		StatItem(int limit) {
			this.limit = limit;
		}
		
		boolean inc() {
			return curValue.incrementAndGet() > limit;
		}
		void dec() {
			curValue.decrementAndGet();
		}
		
	}
	
	HashMap<Integer,StatItem> serviceStats = new HashMap<>();
	HashMap<String,StatItem> msgStats = new HashMap<>();

	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);			
		configLimit(params);
	}

	public void addLimit(int serviceId,int limit) {
		StatItem item = serviceStats.get(serviceId);
    	if( item == null ) {
    		item = new StatItem(limit);
    	}
    	serviceStats.put(serviceId, item);
	}
	
	public void addLimit(int serviceId,int msgId,int limit) {
		String key = serviceId + "." + msgId;
		StatItem item = msgStats.get(key);
    	if( item == null ) {
    		item =new StatItem(limit);
    	}
    	msgStats.put(key, item);		
	}

	public int preCall(RpcContextData ctx,Message req) {
    	int serviceId = ctx.getMeta().getServiceId();
    	int msgId = ctx.getMeta().getMsgId();
    	
    	StatItem item1 = serviceStats.get(serviceId);
    	if( item1 != null ) {
    		boolean failed = item1.inc();
    		if( failed ) {
    			item1.dec();
    			return RetCodes.FLOW_LIMIT;
    		}
    	}
    	
    	String key = serviceId + "." + msgId;
    	StatItem item2 = msgStats.get(key);
    	if( item2 != null ) {
    		boolean failed = item2.inc();
    		if( failed ) {
    			if( item1 != null ) item1.dec();
    			item2.dec();
    			return RetCodes.FLOW_LIMIT;
    		}
    	}

    	return 0;
	}
	
	public void postCall(RpcContextData ctx,Message req,Message res) {
    	int serviceId = ctx.getMeta().getServiceId();
    	int msgId = ctx.getMeta().getMsgId();
    	StatItem item1 = serviceStats.get(serviceId);
    	if( item1 != null ) {
    		item1.dec();
    	}
    	String key = serviceId + "." + msgId;
    	StatItem item2 = msgStats.get(key);
    	if( item2 != null ) {
    		item2.dec();
    	}
	}

}

