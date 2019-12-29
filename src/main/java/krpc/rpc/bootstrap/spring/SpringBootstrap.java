package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.Bootstrap.PluginInfo;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.core.*;
import krpc.rpc.monitor.LogFormatter;
import krpc.rpc.monitor.MonitorPlugin;
import krpc.rpc.web.AutoRoutePlugin;
import krpc.rpc.web.WebPlugin;
import krpc.trace.TraceAdapter;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpringBootstrap {

    static public final SpringBootstrap instance = new SpringBootstrap();

    public ConfigurableApplicationContext spring;
    private Bootstrap bootstrap;
    private RpcApp rpcApp;

    @SuppressWarnings("rawtypes")
    public Bootstrap getBootstrap() {
        if (bootstrap == null) {
            String bootstrapCls = System.getProperty("KRPC_BOOTSTRAP");
            if (bootstrapCls != null && !bootstrapCls.isEmpty()) {
                try {
                    Class cls = Class.forName(bootstrapCls);
                    bootstrap = (Bootstrap) cls.newInstance();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            } else {
                bootstrap = new Bootstrap();
            }
        }

        bootstrap.setEnvVarGetter( (key)-> spring.getEnvironment().getProperty(key,"") );

        return bootstrap;
    }

    public RpcApp getRpcApp() {
        return rpcApp;
    }

    public void setRpcApp(RpcApp rpcApp) {
        this.rpcApp = rpcApp;
    }

    public HashMap<String, List<PluginInfo>> loadSpiBeans() {

        HashMap<String, List<PluginInfo>> beanPlugins = new HashMap<>();
        if (spring == null) return beanPlugins;

        try {
            loadPluginBean(LoadBalance.class, beanPlugins);
            loadPluginBean(RpcPlugin.class, beanPlugins);
            loadPluginBean(Registry.class, beanPlugins);
            loadPluginBean(ErrorMsgConverter.class, beanPlugins);
            loadPluginBean(LogFormatter.class, beanPlugins);
            loadPluginBean(WebPlugin.class, beanPlugins);
            loadPluginBean(DynamicRoutePlugin.class, beanPlugins);
            loadPluginBean(MonitorPlugin.class, beanPlugins);
            loadPluginBean(FallbackPlugin.class, beanPlugins);
            loadPluginBean(TraceAdapter.class, beanPlugins);
            loadPluginBean(ConnectionPlugin.class, beanPlugins);
            loadPluginBean(AutoRoutePlugin.class, beanPlugins);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return beanPlugins;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void loadPluginBean(Class cls, HashMap<String, List<PluginInfo>> beanPlugins) throws Exception {

        Map<String, Object> map = spring.getBeansOfType(cls);
        if (map == null) return;
        List<PluginInfo> list = new ArrayList<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();
            Bootstrap.PluginInfo pi = new Bootstrap.PluginInfo(cls, bean.getClass(), bean, beanName);
            list.add(pi);
        }

        beanPlugins.put(cls.getName(), list);
    }

}

