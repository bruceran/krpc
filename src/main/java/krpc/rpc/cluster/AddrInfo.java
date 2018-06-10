package krpc.rpc.cluster;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import krpc.common.RetCodes;

public class AddrInfo implements Addr {
	
	public static final int MAX_CONNECTIONS = 24;
	
	private static int masks[];
	private static int revMasks[];
	
	static {
		masks = new int[MAX_CONNECTIONS];
		revMasks = new int[MAX_CONNECTIONS];
		int v  = 1;
		for(int i=0;i<MAX_CONNECTIONS;++i) {
			masks[i] = v;
			revMasks[i] = (~v) & 0xff;
			v *= 2;
		}
	}
	
	String addr;
	int connections;
	
	private AtomicInteger status = new AtomicInteger(0); // include all connection status, each bit has a status
	private AtomicInteger current = new AtomicInteger(-1); // current index to get next connect
	private AtomicInteger seq = new AtomicInteger(0);
	private AtomicInteger pending = new AtomicInteger(0);
	private AtomicInteger weight = new AtomicInteger(0); // todo dynamic change
	private AtomicBoolean removeFlag = new AtomicBoolean(false);
	
	static class SecondStat {
		long time;
		int reqs = 0;
		long timeUsedMicros = 0;
		SecondStat(long time) { this.time = time; }
	}
	
	static class AllSecondStat {
		LinkedList<SecondStat> list = new LinkedList<SecondStat>();
		
		synchronized void incReq(long timeUsedMicros) {
			long now = System.currentTimeMillis()/1000;
			if( list.isEmpty() || now > list.getFirst().time ) {
				list.addFirst( new SecondStat(now) );
			}
			SecondStat item = list.getFirst();
			item.reqs++;
			item.timeUsedMicros += timeUsedMicros;
			
			// clear old data
			long clearTime = now - Addr.MAX_SECONDS_ALLOWED;
			while( list.getLast().time <= clearTime ) {
				list.removeLast();
			}
		}
		
		synchronized long getAvgTimeUsed(long secondsBefore) {
			long beforeTime = System.currentTimeMillis()/1000 - secondsBefore;
			int ttlReqs = 0;
			long ttlTimeUsedMicros = 0;
			
			for(SecondStat item: list) {
				if( item.time >= beforeTime ) {
					ttlReqs += item.reqs;
					ttlTimeUsedMicros += item.timeUsedMicros;
				} else {
					break;
				}
			}
			
			if( ttlReqs > 0 ) {
				return ttlTimeUsedMicros / ttlReqs;
			} else {
				return 0;
			}
		}		
	}

	private AllSecondStat allSecondStat = new AllSecondStat();
	
	public AddrInfo(String addr,int connections) { 
		this.addr = addr;
		this.connections = connections;
	}
	
	public long getAvgTimeUsedMicros(int secondsBefore ) {
		return allSecondStat.getAvgTimeUsed(secondsBefore);
	}
	
	public int getWeight() {
		return weight.get();
	}
	
	public void incPending() {
		pending.incrementAndGet();
	}

	public void decPending() {
		pending.decrementAndGet();
	}
    
    public int getPendingCalls() {
    	return pending.get();
    }
	
	public void updateResult(int retCode,long timeUsedMicros) {
		if( !RetCodes.hasExecuted(retCode) ) return;
		allSecondStat.incReq(timeUsedMicros);
	}
    
	public String getAddr() { 
		return addr; 
	}

	public boolean isConnected() {
		return status.get() != 0;
	}

	public boolean isConnected(int index) {
		int v = status.get();
		return (v & masks[index]) != 0;
	}
	
	public void setConnected(int index) {
		while(true) {
			int v = status.get();
			int newv = v | masks[index];
			boolean ok = status.compareAndSet(v, newv);
			if(ok) break;
		} 
	}
	
	public void setDisConnected(int index) {
		while(true) {
			int v = status.get();
			int newv = v & revMasks[index];
			boolean ok = status.compareAndSet(v, newv);
			if(ok) break;
		} 
	}
	
	public int nextConnection() {
		int cur = current.incrementAndGet();
		if( cur >= 10000000 ) current.set(0);
		int index = cur % connections;
		
		int v = status.get();
		
		if( (v & masks[index]) != 0 ) return index;
		for(int i=0;i<connections;++i) {
			if( (v & masks[i]) != 0 ) return i;
		}
		
		return 0;
	}
	
	public void setRemoveFlag(boolean flag) {
		this.removeFlag.set(flag);
	}
	
	public boolean getRemoveFlag() {
		return this.removeFlag.get();
	}
	
    public int nextSequence() {
    	int v = seq.incrementAndGet();
    	if( v >= 10000000 ) seq.set(0);
    	return v;
    }

}
