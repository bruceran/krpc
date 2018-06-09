package krpc.rpc.bootstrap.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import krpc.rpc.bootstrap.ApplicationConfig;
import krpc.rpc.bootstrap.RpcApp;

public class ApplicationConfigBean extends ApplicationConfig implements FactoryBean<RpcApp>,InitializingBean,BeanNameAware,BeanFactoryPostProcessor {
	
	private static int count = 0;
	
	private String id;
	
    public void setBeanName(String name) {
    	id = name;
    }
    
    @Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory0) throws BeansException {
		SpringBootstrap.instance.postProcessBeanFactory(beanFactory0);

	}	
    
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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}
