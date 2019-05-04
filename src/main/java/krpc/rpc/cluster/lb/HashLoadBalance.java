package krpc.rpc.cluster.lb;

import krpc.common.Plugin;
import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.cluster.Weights;
import krpc.rpc.core.ClientContextData;
import krpc.rpc.util.MurmurHash;
import krpc.rpc.util.TypeSafe;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class HashLoadBalance implements LoadBalance {

    String hashField;
    Random rand = new Random();

    public boolean needReqInfo(int serviceId,int msgId) {
        return true;
    }

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        this.hashField = params.get("hashField");
    }

    public int select(List<Addr> addrs, Weights weights, ClientContextData ctx, Map<String,Object> req) {
        int index = getIndex(addrs, req);
        if (index < 0) return rand.nextInt(addrs.size());
        return index;
    }

    int getIndex(List<Addr> addrs, Map<String,Object> req) {
        if (hashField == null || hashField.isEmpty() ) return -1;
        if( req == null ) return -1;
        Object v = req.get(hashField);
        if( v == null || "".equals(v) ) return -1;
        long hash = MurmurHash.hash(TypeSafe.anyToString(v));
        int idx = (int) (hash % addrs.size());
        return idx < 0 ? idx * (-1) : idx;
    }

}

