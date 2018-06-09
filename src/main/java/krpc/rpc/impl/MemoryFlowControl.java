package krpc.rpc.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.protobuf.Message;

import krpc.rpc.core.Continue;
import krpc.rpc.core.FlowControl;
import krpc.rpc.core.RpcContextData;

public class MemoryFlowControl implements FlowControl {
	
	static class StatItem {
		int seconds;
		int limit;
		long curTime;
		int curValue;
		
		StatItem(int seconds,int limit) {
			this.seconds = seconds;
			this.limit = limit;
		}
		
		synchronized boolean update(long now) {
			long time = (now / seconds) * seconds;
			if( time > curTime ) {
				curTime = time;
				curValue = 0;
			}
			curValue++;
			return curValue > limit;
		}
	}
	
	HashMap<Integer,List<StatItem>> serviceStats = new HashMap<>();
	HashMap<String,List<StatItem>> msgStats = new HashMap<>();

	public void addLimit(int serviceId,int seconds,int limit) {
    	List<StatItem> list = serviceStats.get(serviceId);
    	if( list == null ) {
    		list = new ArrayList<StatItem>();
    		list.add( new StatItem(seconds,limit) );
    	}
    	serviceStats.put(serviceId, list);
	}
	
	public void addLimit(int serviceId,int msgId,int seconds,int limit) {
		String key = serviceId + "." + msgId;
    	List<StatItem> list = msgStats.get(key);
    	if( list == null ) {
    		list = new ArrayList<StatItem>();
    		list.add( new StatItem(seconds,limit) );
    	}
    	msgStats.put(key, list);		
	}
	
	
	public boolean isAsync() { return false; }

    public boolean exceedLimit(RpcContextData ctx,Message req,Continue<Boolean> dummy) {
    	int serviceId = ctx.getMeta().getServiceId();
    	int msgId = ctx.getMeta().getMsgId();
    	long now = System.currentTimeMillis()/1000;
    	boolean failed1 = updateServiceStats(serviceId,now);
    	boolean failed2 = updateMsgStats(serviceId,msgId,now);
    	return failed1 || failed2;
    }

    boolean updateServiceStats(int serviceId,long now) {
    	List<StatItem> list = serviceStats.get(serviceId);
    	if( list == null ) return false;
    	return updateStats(list,now);
    }

    boolean updateMsgStats(int serviceId,int msgId,long now) {
    	String key = serviceId + "." + msgId;
    	List<StatItem> list = msgStats.get(key);
    	if( list == null ) return false;
    	return updateStats(list,now);
    }
    
    boolean updateStats(List<StatItem> stats,long now) {
    	boolean failed = false;
    	for(StatItem stat: stats) {
    		if( stat.update(now) ) failed = true;
    	}
    	return failed;
    }
    
}

