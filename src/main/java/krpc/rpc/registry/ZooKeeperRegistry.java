package krpc.rpc.registry;

import krpc.common.*;
import krpc.rpc.core.*;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZooKeeperRegistry implements Registry, InitClose, DynamicRoutePlugin, DumpPlugin, HealthPlugin, AlarmAware {

    static Logger log = LoggerFactory.getLogger(ZooKeeperRegistry.class);

    String addrs;
    boolean enableRegist = true;
    boolean enableDiscover = true;

    int interval = 15;

    // create /dynamicroutes/default/100/routes.json.version 1
    // create /dynamicroutes/default/100/routes.json '{"serviceId":100,"disabled":false,"weights":[{"addr":"192.168.31.27","weight":50},{"addr":"192.168.31.28","weight":50}],"rules":[{"from":"host = 192.168.31.27","to":"host = 192.168.31.27","priority":2},{"from":"host = 192.168.31.28","to":"host = $host","priority":1}]}'

    CuratorFramework client;

    AtomicBoolean healthy = new AtomicBoolean(true);

    ConcurrentHashMap<String, String> versionCache = new ConcurrentHashMap<>();

    Alarm alarm = new DummyAlarm();

    public void init() {

        client = CuratorFrameworkFactory.builder().connectString(addrs)
                .sessionTimeoutMs(60000)
                .connectionTimeoutMs(3000)
                .canBeReadOnly(false)
                .retryPolicy(new RetryOneTime(1000))
                .build();
        client.start();
    }

    public void config(String paramsStr) {

        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);

        addrs = params.get("addrs");

        String s = params.get("enableRegist");
        if (!isEmpty(s)) enableRegist = Boolean.parseBoolean(s);

        s = params.get("enableDiscover");
        if (!isEmpty(s)) enableDiscover = Boolean.parseBoolean(s);

        s = params.get("intervalSeconds");
        if (!isEmpty(s)) interval = Integer.parseInt(s);
    }

    public void close() {
        client.close();
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public int getCheckIntervalSeconds() {
        return interval;
    }

    public int getRefreshIntervalSeconds() {
        return interval;
    }

    public DynamicRouteConfig getConfig(int serviceId, String serviceName, String group) {

        String path = "/dynamicroutes/" + group + "/" + serviceId + "/routes.json";
        String versionPath = path + ".version";

        String key = serviceId + "." + group;
        String oldVersion = versionCache.get(key);
        String newVersion = null;

        try {
            byte[] bytes = client.getData().forPath(versionPath);
            if (bytes == null) return null;
            newVersion = new String(bytes);
        } catch (Exception e) {
            log.error("cannot get routes json version for service " + serviceName + ", exception=" + e.getMessage());
            return null;
        }
        if (oldVersion != null && newVersion != null && oldVersion.equals(newVersion)) {
            return null; // no change
        }

        DynamicRouteConfig config = null;

        try {
            byte[] bytes = client.getData().forPath(path);
            if (bytes == null) return null;
            String json = new String(bytes);
            config = Json.toObject(json, DynamicRouteConfig.class);
            if (config == null) {
                log.error("invalid routes json for service " + serviceName + ", json=" + json);
                return null;
            }
        } catch (Exception e) {
            log.error("cannot get routes json for service " + serviceName + ", exception=" + e.getMessage());
            return null;
        }

        versionCache.put(key, newVersion);
        return config;
    }

    public void register(int serviceId, String serviceName, String group, String addr) {
        if (!enableRegist) return;

        String instanceId = addr;
        String path = "/services/" + group + "/" + serviceId + "/" + instanceId;

        HashMap<String, Object> meta = new HashMap<>();
        meta.put("addr", addr);
        meta.put("group", group);
        meta.put("serviceName", serviceName);
        String data = Json.toJson(meta);

        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path, data.getBytes());
            healthy.set(true);
        } catch (Exception e) {
            if (e.getMessage().indexOf("NodeExists") < 0) {
                healthy.set(false);
                log.error("cannot register service " + serviceName + ", exception=" + e.getMessage());
            }
        }
    }

    public void deregister(int serviceId, String serviceName, String group, String addr) {

        if (!enableRegist) return;

        String instanceId = addr;
        String path = "/services/" + group + "/" + serviceId + "/" + instanceId;

        try {
            client.delete().forPath(path);
            healthy.set(true);
        } catch (Exception e) {
            healthy.set(false);
            log.error("cannot deregister service " + serviceName + ", exception=" + e.getMessage());
        }

    }

    public String discover(int serviceId, String serviceName, String group) {

        if (!enableDiscover) return null;

        String path = "/services/" + group + "/" + serviceId;

        List<String> list = null;

        try {
            list = client.getChildren().forPath(path);
            healthy.set(true);
        } catch (Exception e) {
            log.error("cannot discover service " + serviceName + ", exception=" + e.getMessage());
            healthy.set(false);
            return null;
        }

        TreeSet<String> set = new TreeSet<>();
        for (String addr : list) {
            set.add(addr);
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
        list.add(new HealthStatus(alarmId,false,"zk_registry connect failed","zookeeper",addrs.replaceAll(",","#")));
    }


    @Override
    public void dump(Map<String, Object> metrics) {
        boolean b = healthy.get();
        if( b ) return;

        alarm.alarm(Alarm.ALARM_TYPE_REGDIS,"zk_registry connect failed","zookeeper",addrs.replaceAll(",","#"));
        metrics.put("krpc.consul.errorCount",1);
    }

    @Override
    public void setAlarm(Alarm alarm) {
        this.alarm = alarm;
    }
}

