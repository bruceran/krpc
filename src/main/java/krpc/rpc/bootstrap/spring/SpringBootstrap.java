package krpc.rpc.bootstrap.spring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ConfigurableApplicationContext;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.Bootstrap.PluginInfo;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.core.DynamicRoutePlugin;
import krpc.rpc.core.ErrorMsgConverter;
import krpc.rpc.core.FallbackPlugin;
import krpc.rpc.core.RpcPlugin;
import krpc.rpc.core.Registry;
import krpc.rpc.monitor.LogFormatter;
import krpc.rpc.monitor.MonitorPlugin;
import krpc.rpc.web.WebPlugin;

public class SpringBootstrap {
	
	static public final SpringBootstrap instance = new SpringBootstrap();

	public ConfigurableApplicationContext spring;
	private Bootstrap bootstrap;
	private RpcApp rpcApp;
	
	@SuppressWarnings("rawtypes")
	public Bootstrap getBootstrap() {
		if( bootstrap == null ) {
			String bootstrapCls = System.getProperty("KRPC_BOOTSTRAP");
			if( bootstrapCls != null && !bootstrapCls.isEmpty() ) {
				try {
					Class cls = Class.forName(bootstrapCls);
					bootstrap = (Bootstrap)cls.newInstance();
				} catch(Throwable e) {
					throw new RuntimeException(e);
				}
			} else {
				bootstrap = new Bootstrap();
			}
		}
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
		if( spring == null ) return beanPlugins;
		
		try {
			loadBean(LoadBalance.class,beanPlugins);
			loadBean(RpcPlugin.class,beanPlugins);
			loadBean(Registry.class,beanPlugins);
			loadBean(ErrorMsgConverter.class,beanPlugins);
			loadBean(LogFormatter.class,beanPlugins);
			loadBean(WebPlugin.class,beanPlugins);
			loadBean(DynamicRoutePlugin.class,beanPlugins);
			loadBean(MonitorPlugin.class,beanPlugins);
			loadBean(FallbackPlugin.class,beanPlugins);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		
		return beanPlugins;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void loadBean(Class cls,HashMap<String, List<PluginInfo>> beanPlugins)  throws Exception {
		
		Map<String,Object> map = spring.getBeansOfType(cls);
		if( map == null ) return;
		List<PluginInfo> list = new ArrayList<>();
		
		for(Map.Entry<String, Object> entry: map.entrySet()) {
			String beanName =entry.getKey();
			Object bean = entry.getValue();
			Bootstrap.PluginInfo pi = new Bootstrap.PluginInfo(cls,bean.getClass(),bean,beanName);
			list.add(pi);
		}

		beanPlugins.put(cls.getName(), list);
	}
	
}

