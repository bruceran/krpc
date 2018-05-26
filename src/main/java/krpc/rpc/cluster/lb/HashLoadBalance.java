package krpc.rpc.cluster.lb;

import java.util.Map;
import java.util.Random;

import com.google.protobuf.Message;

import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.core.Plugin;
import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.util.MurmurHash;

public class HashLoadBalance implements LoadBalance {
	
	String getter;
	Random rand = new Random();
	
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("hashField");
		if( s != null && s.length() > 0 )
			getter = "get" + Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public int select(Addr[] addrs,int serviceId,int msgId,Message req) {
		int index = getIndex(addrs,req);
		if( index < 0 ) return rand.nextInt(addrs.length);
		return index;
	}
	
	int getIndex(Addr[] addrs,Message req) {
		if( getter == null ) return -1;
		Object o = ReflectionUtils.invokeMethod(req,getter); // todo dynamic
		if( o == null ) return -1;

		long hash = MurmurHash.hash(o.toString());
		int idx = (int)(hash % addrs.length);
		return idx < 0 ? idx * (-1) : idx;
	}
	
}

/*

TreeMap<Long, Integer> treeMap = new TreeMap<>();
for(int i=0;i<addrs.length;++i) {
    for (int j=0;j<50;++j) {
        long h = MurmurHash.hash(addrs[i] + "-" + j);
        treeMap.put(h, i);
    }
}
SortedMap<Long,Integer>  tail = treeMap.tailMap(hash);
if (tail == null || tail.isEmpty())
    return treeMap.get(treeMap.firstKey());
else
    return tail.get(tail.firstKey());
    
*/
