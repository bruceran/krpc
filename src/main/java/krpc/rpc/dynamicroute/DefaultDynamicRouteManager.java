package krpc.rpc.dynamicroute;

import com.fasterxml.jackson.core.type.TypeReference;
import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.common.Json;
import krpc.common.StartStop;
import krpc.rpc.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DefaultDynamicRouteManager implements DynamicRouteManager, InitClose, StartStop {

    static Logger log = LoggerFactory.getLogger(DefaultDynamicRouteManager.class);

    Map<Integer, DynamicRouteConfig> routes = new HashMap<>();
    List<ConfigItem> configItems = new ArrayList<ConfigItem>();

    private String dataDir;
    private String localFile = "dynamicroutes.cache";

    private int startInterval = 1000;
    private int checkInterval = 1000;

    DynamicRoutePlugin dynamicRoutePlugin;
    ServiceMetas serviceMetas;

    Timer timer;

    public DefaultDynamicRouteManager(String dataDir) {
        this.dataDir = dataDir;
    }

    public void addConfig(int serviceId, String group, DynamicRouteManagerCallback callback) {
        configItems.add(new ConfigItem(serviceId, group, callback));
    }

    public void init() {

        if (!(dynamicRoutePlugin instanceof Registry)) {
            InitCloseUtils.init(dynamicRoutePlugin);
        }

        loadFromLocal();
    }

    public void start() {

        if (!(dynamicRoutePlugin instanceof Registry)) {
            InitCloseUtils.start(dynamicRoutePlugin);
        }

        boolean changed = refreshConfig();

        notifyRouteChanged();

        if (changed) {
            saveToLocal();
        }

        if (configItems.size() > 0) {
            timer = new Timer("krpc_dynamicroute_timer");
            timer.schedule(new TimerTask() {
                public void run() {
                    refresh();
                }
            }, startInterval, checkInterval);
        }
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        if (!(dynamicRoutePlugin instanceof Registry)) {
            InitCloseUtils.stop(dynamicRoutePlugin);
        }
    }

    public void close() {

        if (!(dynamicRoutePlugin instanceof Registry)) {
            InitCloseUtils.close(dynamicRoutePlugin);
        }
    }

    void refresh() {

        boolean changed = refreshConfig();
        if (changed) {
            saveToLocal();
            notifyRouteChanged();
        }

    }

    void notifyRouteChanged() {
        for (int i = 0; i < configItems.size(); ++i) {
            ConfigItem item = configItems.get(i);
            DynamicRouteConfig config = routes.get(item.serviceId);
            if (config != null) {
                item.callback.routeConfigChanged(config);
            }
        }
    }

    boolean refreshConfig() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < configItems.size(); ++i) {

            ConfigItem item = configItems.get(i);
            int seconds = dynamicRoutePlugin.getRefreshIntervalSeconds();
            if (now - item.lastRefresh < seconds * 1000) continue;

            item.lastRefresh = now;

            String serviceName = serviceMetas.getServiceName(item.serviceId);
            DynamicRouteConfig config = dynamicRoutePlugin.getConfig(item.serviceId, serviceName, item.group);
            if (config != null) { // null is failed, not null is success
                DynamicRouteConfig oldConfig = routes.get(item.serviceId);
                if (oldConfig == null || !oldConfig.equals(config)) {
                    routes.put(item.serviceId, config);
                    changed = true;
                }
            }
        }
        return changed;
    }

    void loadFromLocal() {
        Path path = Paths.get(dataDir, localFile);
        try {
            byte[] bytes = Files.readAllBytes(path);
            String json = new String(bytes);
            routes = Json.toObject(json, new TypeReference<Map<Integer, DynamicRouteConfig>>() {
            });
        } catch (Exception e) {
            // log.info("cannot load from file, path="+path);
        }
    }

    void saveToLocal() {
        Path path = Paths.get(dataDir, localFile);
        try {
            String json = Json.toJson(routes);
            byte[] bytes = json.getBytes();
            Files.write(path, bytes);
        } catch (Exception e) {
            log.error("cannot write to file, path=" + path);
        }
    }

    public ServiceMetas getServiceMetas() {
        return serviceMetas;
    }

    public void setServiceMetas(ServiceMetas serviceMetas) {
        this.serviceMetas = serviceMetas;
    }

    public DynamicRoutePlugin getDynamicRoutePlugin() {
        return dynamicRoutePlugin;
    }

    public void setDynamicRoutePlugin(DynamicRoutePlugin dynamicRoutePlugin) {
        this.dynamicRoutePlugin = dynamicRoutePlugin;
    }


    static class ConfigItem {
        int serviceId;
        String group;
        DynamicRouteManagerCallback callback;
        long lastRefresh = 0;

        ConfigItem(int serviceId, String group, DynamicRouteManagerCallback callback) {
            this.serviceId = serviceId;
            this.group = group;
            this.callback = callback;
        }
    }

}

