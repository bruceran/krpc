package krpc.rpc.cluster.lb;

import java.util.Map;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.core.Plugin;

public class ResponseTimeLoadBalance implements LoadBalance {

	int seconds = 3;
	int rrIndex = -1;
	
	public boolean needCallStats() { return true; }

	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("seconds");
		if( s != null && s.length() > 0 )
			this.seconds = Integer.parseInt( s );
		if( this.seconds > Addr.MAX_SECONDS_ALLOWED )
			throw new RuntimeException("ResponseTimeLoadBalance seconds is too large");
	}
	
	public int select(Addr[] addrs,int serviceId,int msgId,Message req) {
		long min = Long.MAX_VALUE;
		int idx = -1;
		for(int i=0;i<addrs.length;++i) {
			long t =  addrs[i].getAvgTimeUsed(seconds);
			if( t < min ) {
				min = t;
				idx = i;
			}
		}
		if( idx >= 0 ) return idx;
		
		rrIndex++;
		if( rrIndex >= addrs.length ) rrIndex = 0;
		return rrIndex;
	}
}
