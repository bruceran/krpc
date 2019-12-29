package krpc.rpc.dynamicroute;

import krpc.common.Json;
import krpc.common.Plugin;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;
import krpc.rpc.core.DynamicRouteConfig;
import krpc.rpc.core.DynamicRoutePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConsulDynamicRoutePlugin extends AbstractHttpDynamicRoutePlugin implements DynamicRoutePlugin {

    static Logger log = LoggerFactory.getLogger(ConsulDynamicRoutePlugin.class);

    String routesUrlTemplate;

    int interval = 15;

    String aclToken;

    HashSet<Integer> registeredServiceIds = new HashSet<>();

    // curl -X PUT http://192.168.31.144:8500/v1/kv/dynamicroutes/default/100/routes.json.version -d 1
    // curl -X PUT http://192.168.31.144:8500/v1/kv/dynamicroutes/default/100/routes.json -d '{"serviceId":100,"disabled":false,"weights":[{"addr":"192.168.31.27","weight":50},{"addr":"192.168.31.28","weight":50}],"rules":[{"from":"host = 192.168.31.27","to":"host = 192.168.31.27","priority":2},{"from":"host = 192.168.31.28","to":"host = $host","priority":1}]}'

    // curl -H "X-Consul-Token: dde69310-cec2-eab7-4082-c53cbd556b25"  -X GET http://10.1.20.16:8500/v1/kv/dynamicroutes/default/100/routes.json.version
    // curl -H "X-Consul-Token: dde69310-cec2-eab7-4082-c53cbd556b25" -X PUT http://10.1.20.16:8500/v1/kv/dynamicroutes/default/100/routes.json.version -d 5555

    ConcurrentHashMap<String, String> versionCache = new ConcurrentHashMap<>();

    public void init() {
        routesUrlTemplate = "http://%s/v1/kv/dynamicroutes";
        super.init();
    }

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        String s = params.get("intervalSeconds");
        if (!isEmpty(s)) interval = Integer.parseInt(s);

        s = params.get("aclToken");
        if (!isEmpty(s)) aclToken = s;

        super.config(params);
    }

    public int getRefreshIntervalSeconds() {
        return interval;
    }

    public DynamicRouteConfig getConfig(int serviceId, String serviceName, String group) {

        String path = routesUrlTemplate + "/" + group + "/" + serviceId + "/routes.json";
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

        String url = String.format(path, addr());
        url += "?raw"; // return json only
        HttpClientReq req = new HttpClientReq("GET", url);

        if(!isEmpty(aclToken)) {
            req.addHeader("X-Consul-Token", aclToken);
        }

        HttpClientRes res = hc.call(req);
        if (res.getRetCode() != 0 || res.getHttpCode() != 200) {
            log.error("cannot get config " + url);
            if (res.getHttpCode() != 404) nextAddr();
            return null;
        }

        String data = res.getContent();
        return data;
    }

}


