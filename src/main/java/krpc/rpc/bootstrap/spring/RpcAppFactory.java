package krpc.rpc.bootstrap.spring;


import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.ServiceConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.*;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.List;

public class RpcAppFactory implements FactoryBean<RpcApp>, ApplicationContextAware, ApplicationListener<ApplicationEvent> {

    private Bootstrap bootstrap;
    private RpcApp rpcApp;
    private boolean postBuilt = false;

    public RpcAppFactory() {
    }

    @Override
    public void setApplicationContext(ApplicationContext beanFactory) throws BeansException {
        SpringBootstrap.instance.spring = (ConfigurableApplicationContext) beanFactory;
    }

    public void build() {
        if (rpcApp != null) return;
        bootstrap = SpringBootstrap.instance.getBootstrap();
        bootstrap.setTwoPhasesBuild(true);
        bootstrap.mergePlugins(SpringBootstrap.instance.loadSpiBeans());
        rpcApp = bootstrap.build();
        SpringBootstrap.instance.setRpcApp(rpcApp);
    }

    void postBuild() {

        List<ServiceConfig> cl = bootstrap.getServiceList();
        for( ServiceConfig c: cl) {
            String impl = c.getImpl() == null ? null : c.getImpl().toString();
            Object bean = loadBean(impl, c.getInterfaceName(), SpringBootstrap.instance.spring);
            if (bean == null) throw new RuntimeException("bean not found for service " + c.getInterfaceName());
            c.setImpl(bean);
        }

        bootstrap.postBuild();
    }

    Object loadBean(String impl, String interfaceName, BeanFactory beanFactory) {
        if (interfaceName == null) return null;

        String beanName;
        if (impl != null && !impl.isEmpty()) {
            beanName = impl;
        } else {
            int p = interfaceName.lastIndexOf(".");
            if (p < 0) return null;
            String name = interfaceName.substring(p + 1);
            beanName = name.substring(0, 1).toLowerCase() + name.substring(1);
        }
        try {
            Object o = beanFactory.getBean(beanName);
            return o;
        } catch (Exception e1) {
            try {
                Object o = beanFactory.getBean(Class.forName(interfaceName));
                return o;
            } catch (Throwable e2) {
                return null;
            }
        }
    }

    public void init() throws Exception {

        RpcApp rpcApp = getObject();

        if( !postBuilt ) {
            postBuild();
            postBuilt = true;
        }

        rpcApp.init();
    }

    public void close() throws Exception {
        getObject().close();
    }

    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            int delayStart = SpringBootstrap.instance.getBootstrap().getAppConfig().getDelayStart();
            rpcApp.start(delayStart);
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public RpcApp getObject() throws Exception {
        if (rpcApp == null) {
            build();
        }
        return rpcApp;
    }

    @Override
    public Class<?> getObjectType() {
        return RpcApp.class;
    }


}
