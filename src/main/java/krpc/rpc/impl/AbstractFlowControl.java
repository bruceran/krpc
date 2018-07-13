package krpc.rpc.impl;

import java.util.Map;

abstract public class AbstractFlowControl {

    void configLimit(Map<String, String> params) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.startsWith("service.")) {
                String[] keys = key.split("\\.");
                int serviceId = Integer.parseInt(keys[1]);
                int seconds = Integer.parseInt(keys[2]);
                int limit = Integer.parseInt(value);
                addLimit(serviceId, seconds, limit);
            }
            if (key.startsWith("msg.")) {
                String[] keys = key.split("\\.");
                int serviceId = Integer.parseInt(keys[1]);
                int seconds = Integer.parseInt(keys[3]);
                int limit = Integer.parseInt(value);
                String[] mm = keys[2].split("#");
                for (String m : mm) {
                    int msgId = Integer.parseInt(m);
                    addLimit(serviceId, msgId, seconds, limit);
                }
            }
        }
    }

    abstract public void addLimit(int serviceId, int seconds, int limit);

    abstract public void addLimit(int serviceId, int msgId, int seconds, int limit);
}

