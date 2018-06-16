package krpc.rpc.bootstrap.spring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

import krpc.rpc.bootstrap.ApplicationConfig;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.Bootstrap.PluginInfo;
import krpc.rpc.bootstrap.RefererConfig;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.core.ErrorMsgConverter;
import krpc.rpc.core.RpcPlugin;
import krpc.rpc.core.Registry;
import krpc.rpc.monitor.LogFormatter;
import krpc.rpc.web.WebPlugin;

public class SpringBootstrap {
	static public final SpringBootstrap instance = new SpringBootstrap();

	static Logger log = LoggerFactory.getLogger(SpringBootstrap.class);
	
	public ConfigurableApplicationContext spring;
	
	boolean postProcessed = false;
	boolean inited = false;
	boolean stopped = false;
	boolean closed = false;
	private Bootstrap bootstrap = null;
	RpcApp rpcApp = null;
	
	public Bootstrap getBootstrap() {
		if( bootstrap == null ) {
			String bootstrapCls = System.getProperty("KRPC_BOOTSTRAP");
			if( bootstrapCls != null && !bootstrapCls.isEmpty() ) {
				try {
					Class cls = Class.forName(bootstrapCls);
					bootstrap = (Bootstrap)cls.newInstance();
				} catch(Exception e) {
					throw new RuntimeException(e);
				}
			} else {
				bootstrap = new Bootstrap();
			}
		}
		return bootstrap;
	}
	
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory0) throws BeansException {
		if(postProcessed) return;
		
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)beanFactory0;

		String appBeanName = null;
		ApplicationConfig ac = bootstrap.getAppConfig();
		if( ac instanceof  ApplicationConfigBean ) {
			ApplicationConfigBean appBean = (ApplicationConfigBean)ac;
			appBeanName = appBean.getId();
		}  
		
		for(RefererConfig c: bootstrap.getRefererList()) {
			String id = c.getId();
			String interfaceName = c.getInterfaceName();
			String beanName = generateBeanName(id,interfaceName);
			registerAsyncReferer(beanName+"Async",interfaceName+"Async",beanFactory,appBeanName);
		}
	
		postProcessed = true;
	}	
	
	String generateBeanName(String id, String interfaceName) {
		if( id != null && !id.isEmpty()) return id;
		int p = interfaceName.lastIndexOf(".");
		String name = interfaceName.substring(p+1);
		name = name.substring(0,1).toLowerCase()+name.substring(1);
		return name;
	}
	
	void registerAsyncReferer(String beanName,String interfaceName,DefaultListableBeanFactory beanFactory,String appBeanName) {
		log.info("register referer "+interfaceName+", beanName="+beanName);
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(RefererFactory.class);
        beanDefinitionBuilder.addConstructorArgValue(beanName);
        beanDefinitionBuilder.addConstructorArgValue(interfaceName);
        if( appBeanName != null )
        	beanDefinitionBuilder.addDependsOn(appBeanName);
        beanDefinitionBuilder.setLazyInit(true);
        beanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());			
	}		

	public RpcApp getRpcApp() {
		return rpcApp;
	}
	
	public void setRpcApp(RpcApp rpcApp) {
		this.rpcApp = rpcApp;
	}
	
    public void build() {
    	if( inited ) return;
		bootstrap.mergePlugins(loadSpiBeans());
    	rpcApp = bootstrap.build();
    	rpcApp.initAndStart();
    	inited = true;       	
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
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		
		return beanPlugins;
	}

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
	
	public void stop()  {
		if(stopped) return;
		rpcApp.stop();
		stopped = true;
	}
	
	public void close()  {
		if(closed) return;
		rpcApp.close();
		closed = true;
		rpcApp = null;
		bootstrap = new Bootstrap();
	}

}

