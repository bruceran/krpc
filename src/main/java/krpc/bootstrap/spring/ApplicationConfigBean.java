package krpc.bootstrap.spring;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import krpc.bootstrap.ApplicationConfig;
import krpc.bootstrap.RpcApp;

public class ApplicationConfigBean extends ApplicationConfig implements FactoryBean<RpcApp>,InitializingBean {
	
	private static int count = 0;
	
    public void afterPropertiesSet() throws Exception {
    	count++;
    	if( count >= 2 ) throw new RuntimeException("only one ApplicationConfigBean can be specified");
    	SpringBootstrap.instance.bootstrap.setAppConfig(this);
    }
    
    @Override
    public boolean isSingleton() {
        return true;
    }
    
    @Override
    public RpcApp getObject() throws Exception {
        return SpringBootstrap.instance.rpcApp;
    }

    @Override
    public Class<?> getObjectType() {
    	return RpcApp.class;
    }

}
