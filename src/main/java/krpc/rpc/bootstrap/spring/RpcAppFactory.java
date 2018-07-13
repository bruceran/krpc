package krpc.rpc.bootstrap.spring;


import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.*;
import org.springframework.context.event.ContextRefreshedEvent;

public class RpcAppFactory implements FactoryBean<RpcApp>, ApplicationContextAware, ApplicationListener<ApplicationEvent> {

    private RpcApp rpcApp;

    public RpcAppFactory() {
    }

    @Override
    public void setApplicationContext(ApplicationContext beanFactory) throws BeansException {
        SpringBootstrap.instance.spring = (ConfigurableApplicationContext) beanFactory;
    }

    public void build() {
        if (rpcApp != null) return;
        Bootstrap bootstrap = SpringBootstrap.instance.getBootstrap();
        bootstrap.mergePlugins(SpringBootstrap.instance.loadSpiBeans());
        rpcApp = bootstrap.build();
        SpringBootstrap.instance.setRpcApp(rpcApp);
    }

    public void init() throws Exception {
        getObject().init();
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
