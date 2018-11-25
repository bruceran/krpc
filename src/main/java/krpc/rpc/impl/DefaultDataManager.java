package krpc.rpc.impl;

import krpc.common.InitClose;
import krpc.rpc.core.DataManager;
import krpc.rpc.core.DataManagerCallback;
import krpc.rpc.core.DumpPlugin;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.util.QuickTimerCallback;
import krpc.rpc.util.QuickTimerEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDataManager implements DataManager, QuickTimerCallback, InitClose, DumpPlugin {

    static Logger log = LoggerFactory.getLogger(DefaultDataManager.class);

    ConcurrentHashMap<String, RpcClosure> data = new ConcurrentHashMap<String, RpcClosure>();
    DataManagerCallback callback;
    QuickTimerEngine qte;

    public DefaultDataManager(DataManagerCallback callback) {
        this.callback = callback;
    }

    public void init() {
        qte = new QuickTimerEngine(this);
        qte.init();
    }

    public void close() {
        qte.close();
    }

    String getKey(RpcClosure closure) {
        return closure.getCtx().getConnId() + ":" + closure.getCtx().getMeta().getSequence();
    }

    public void add(RpcClosure closure) {
        String key = getKey(closure);
        data.put(key, closure);
        qte.newTimer(closure.getCtx().getMeta().getTimeout(), key); // always trigger the timeout method callback
    }

    public void remove(RpcClosure closure) {
        String key = getKey(closure);
        data.remove(key);
    }

    public RpcClosure remove(String connId, int sequence) {
        String key = connId + ":" + sequence;
        RpcClosure closure = data.remove(key);
        return closure;
    }

    public void timeout(Object o) {
        String key = (String) o;
        RpcClosure closure = data.remove(key);
        if (closure != null) { // closure is already removed before if not timeout
            callback.timeout(closure);
        }
    }

    public void disconnected(String connId) {
        ArrayList<String> keys = new ArrayList<String>();
        String prefix = connId + ":";
        for (Map.Entry<String, RpcClosure> kv : data.entrySet()) {
            if (kv.getKey().startsWith(prefix)) keys.add(kv.getKey());
        }
        for (String key : keys) {
            RpcClosure closure = data.remove(key);
            if (closure != null)
                callback.disconnected(closure);
        }
    }

    @Override
    public void dump(Map<String, Object> metrics) {
        metrics.put("krpc.datamanager.size",data.size());
    }
}
