package krpc.rpc.registry;

import krpc.common.*;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;
import krpc.rpc.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class EtcdRegistry extends AbstractHttpRegistry implements DynamicRoutePlugin, DumpPlugin, HealthPlugin, AlarmAware {

    static Logger log = LoggerFactory.getLogger(EtcdRegistry.class);

    String basePathTemplate;
    String routesPathTemplate;

    int ttl = 90;
    int interval = 15;

    // curl "http://192.168.31.144:2379/v2/keys/services"
    // curl "http://192.168.31.144:2379/v2/keys/services/default/100"
    // curl -X PUT "http://192.168.31.144:2379/v2/keys/dynamicroutes/default/100/routes.json.version" -d  value=1
    // curl -X PUT "http://192.168.31.144:2379/v2/keys/dynamicroutes/default/100/routes.json" -d value=%7B%22serviceId%22%3A100%2C%22disabled%22%3Afalse%2C%22weights%22%3A%5B%7B%22addr%22%3A%22192.168.31.27%22%2C%22weight%22%3A50%7D%2C%7B%22addr%22%3A%22192.168.31.28%22%2C%22weight%22%3A50%7D%5D%2C%22rules%22%3A%5B%7B%22from%22%3A%22host%20%3D%20192.168.31.27%22%2C%22to%22%3A%22host%20%3D%20192.168.31.27%22%2C%22priority%22%3A2%7D%2C%7B%22from%22%3A%22host%20%3D%20192.168.31.28%22%2C%22to%22%3A%22host%20%3D%20%24host%22%2C%22priority%22%3A1%7D%5D%7D

    AtomicBoolean healthy = new AtomicBoolean(true);

    ConcurrentHashMap<String, String> versionCache = new ConcurrentHashMap<>();

    Alarm alarm = new DummyAlarm();

    public void init() {
        basePathTemplate = "http://%s/v2/keys/services";
        routesPathTemplate = "http://%s/v2/keys/dynamicroutes";
        super.init();
    }

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        String s = params.get("ttlSeconds");
        if (!isEmpty(s)) ttl = Integer.parseInt(s);
        s = params.get("intervalSeconds");
        if (!isEmpty(s)) interval = Integer.parseInt(s);
        super.config(params);
    }

    public int getCheckIntervalSeconds() {
        return interval;
    }

    public int getRefreshIntervalSeconds() {
        return interval;
    }

    public DynamicRouteConfig getConfig(int serviceId, String serviceName, String group) {

        String path = routesPathTemplate + "/" + group + "/" + serviceId + "/routes.json";
        String versionPath = path + ".version";

        String key = serviceId + "." + group;
        String oldVersion = versionCache.get(key);

        String newVersion = getData(versionPath);
        if (newVersion == null || newVersion.isEmpty()) {
            log.error("cannot get routes json version for service " + serviceName);
            return null;
        }

        if (oldVersion != null && newVersion != null && oldVersion.equals(newVersion)) {
            return null; // no change
        }

        String json = getData(path);
        if (json == null || json.isEmpty()) {
            log.error("cannot get routes json for service " + serviceName);
            return null;
        }

        DynamicRouteConfig config = Json.toObject(json, DynamicRouteConfig.class);
        if (config == null) return null;

        versionCache.put(key, newVersion);
        return config;
    }

    public String getData(String path) {

        if (hc == null) return null;

        String getPath = String.format(path, addr());
        HttpClientReq req = new HttpClientReq("GET", getPath);

        HttpClientRes res = hc.call(req);
        if (res.getRetCode() != 0 || res.getHttpCode() != 200) {
            log.error("cannot get data " + getPath + ", content=" + res.getContent());
            if (res.getHttpCode() != 404) nextAddr();
            return null;
        }

        String json = res.getContent();
        Map<String, Object> m = Json.toMap(json);
        if (m == null) {
            nextAddr();
            return null;
        }

        if (m.containsKey("errorCode") || !"get".equals(m.get("action"))) {
            log.error("cannot get data " + getPath + ", content=" + res.getContent());
            return null;
        }

        Map node = (Map) m.get("node");
        if (node == null || node.size() == 0) return "";

        String value = (String) node.get("value");
        if (value == null) value = "";
        return value;
    }

    public void register(int serviceId, String serviceName, String group, String addr) {
        if (!enableRegist) return;
        if (hc == null) return;

        String instanceId = addr;

        String basePath = String.format(basePathTemplate, addr());
        String url = basePath + "/" + group + "/" + serviceId + "/" + instanceId + "?ttl=" + ttl;
        Map<String, String> valueMap = new HashMap<>();
        valueMap.put("addr", addr);
        valueMap.put("serviceName", serviceName);
        String json = Json.toJson(valueMap);
        String value = "value=" + encode(json);
        HttpClientReq req = new HttpClientReq("PUT", url).addHeader("content-type", "application/x-www-form-urlencoded").setContent(value);

        HttpClientRes res = hc.call(req);
        if (res.getRetCode() != 0 || res.getHttpCode() != 201 && res.getHttpCode() != 200) {
            log.error("cannot register service " + serviceName + ", content=" + res.getContent());
            nextAddr();
            healthy.set(false);
            return;
        }

        json = res.getContent();
        Map<String, Object> m = Json.toMap(json);
        if (m == null) {
            nextAddr();
            healthy.set(false);
            return;
        }

        if (m.containsKey("errorCode") || !"set".equals(m.get("action"))) {
            log.error("cannot register service " + serviceName + ", content=" + res.getContent());
            healthy.set(false);
        } else {
            healthy.set(true);
        }
    }

    public void deregister(int serviceId, String serviceName, String group, String addr) {
        if (!enableRegist) return;
        if (hc == null) return;

        String instanceId = addr;

        String basePath = String.format(basePathTemplate, addr());
        String url = basePath + "/" + group + "/" + serviceId + "/" + instanceId;
        HttpClientReq req = new HttpClientReq("DELETE", url);

        HttpClientRes res = hc.call(req);
        if (res.getRetCode() != 0 || res.getHttpCode() != 200) {
            log.error("cannot deregister service " + serviceName + ", content=" + res.getContent());
            nextAddr();
            healthy.set(false);
            return;
        }

        String json = res.getContent();
        Map<String, Object> m = Json.toMap(json);
        if (m == null) {
            nextAddr();
            healthy.set(false);
            return;
        }

        if (m.containsKey("errorCode") || !"delete".equals(m.get("action"))) {
            log.error("cannot deregister service " + serviceName + ", content=" + res.getContent());
            healthy.set(false);
        } else {
            healthy.set(true);
        }
    }

    @SuppressWarnings("rawtypes")
    public String discover(int serviceId, String serviceName, String group) {
        if (!enableDiscover) return null;
        if (hc == null) return null;

        String basePath = String.format(basePathTemplate, addr());
        String url = basePath + "/" + group + "/" + serviceId;
        HttpClientReq req = new HttpClientReq("GET", url);

        HttpClientRes res = hc.call(req);
        if (res.getRetCode() != 0 || res.getHttpCode() != 200) {
            log.error("cannot discover service " + serviceName + ", content=" + res.getContent());
            if (res.getHttpCode() != 404) nextAddr();
            healthy.set(false);
            return null;
        }

        String json = res.getContent();
        Map<String, Object> m = Json.toMap(json);
        if (m == null) {
            nextAddr();
            healthy.set(false);
            return null;
        }

        if (m.containsKey("errorCode") || !"get".equals(m.get("action"))) {
            log.error("cannot discover service " + serviceName + ", content=" + res.getContent());
            healthy.set(false);
            return null;
        } else {
            healthy.set(true);
        }

        TreeSet<String> set = new TreeSet<>();

        Map node = (Map) m.get("node");
        if (node == null || node.size() == 0) return "";
        List nodelist = (List) node.get("nodes");
        if (nodelist == null || nodelist.size() == 0) return "";

        for (Object o : nodelist) {
            if (o instanceof Map) {
                Map mm = (Map) o;
                if (mm != null) {
                    String value = (String) mm.get("value");
                    Map valueMap = Json.toMap(value);
                    set.add((String) valueMap.get("addr"));
                }
            }
        }

        StringBuilder b = new StringBuilder();
        for (String key : set) {
            if (b.length() > 0) b.append(",");
            b.append(key);
        }
        String s = b.toString();
        return s;
    }

    @Override
    public void healthCheck(List<HealthStatus> list) {
        boolean b = healthy.get();
        if( b ) return;
        String alarmId = alarm.getAlarmId(Alarm.ALARM_TYPE_REGDIS);
        list.add(new HealthStatus(alarmId,false,"etcd_registry connect failed"));
    }

    @Override
    public void dump(Map<String, Object> metrics) {
        boolean b = healthy.get();
        if( b ) return;

        alarm.alarm(Alarm.ALARM_TYPE_REGDIS,"etcd_registry has exception");
        metrics.put("krpc.consul.errorCount",1);
    }

    @Override
    public void setAlarm(Alarm alarm) {
        this.alarm = alarm;
    }
}

