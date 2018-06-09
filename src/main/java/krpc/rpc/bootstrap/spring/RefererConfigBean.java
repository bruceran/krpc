package krpc.rpc.bootstrap.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.core.env.Environment;

import krpc.rpc.bootstrap.RefererConfig;

public class RefererConfigBean<T> extends RefererConfig  implements FactoryBean<T>, InitializingBean, BeanNameAware,  ApplicationContextAware,
	ApplicationListener<ApplicationEvent>, BeanFactoryPostProcessor {

    public void setBeanName(String name) {
    	if( !name.startsWith(RefererConfigBean.class.getName()) )
    		setId(name);
    }
    
    @Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory0) throws BeansException {
		SpringBootstrap.instance.postProcessBeanFactory(beanFactory0);

	}	
    
    public void afterPropertiesSet() throws Exception {
    	String group = getGroup();
    	if( group == null || group.isEmpty()) {
    		Environment environment = (Environment)SpringBootstrap.instance.spring.getBean("environment");
    		group = environment.getProperty("spring.profiles.active");
    		setGroup(group);
    	}    	
        SpringBootstrap.instance.bootstrap.addReferer(this);
    }

    public void onApplicationEvent(ApplicationEvent event) {
    	if( event instanceof ContextRefreshedEvent ) {
        	SpringBootstrap.instance.build();
    	}
    	if( event instanceof ContextStoppedEvent ) {
        	SpringBootstrap.instance.stop();
    	}    	
    	if( event instanceof ContextClosedEvent ) {
    		SpringBootstrap.instance.close();
    	}
    }
	
    @Override
	public void setApplicationContext(ApplicationContext beanFactory) throws BeansException {
		SpringBootstrap.instance.spring = (ConfigurableApplicationContext)beanFactory;
	}
	
    @Override
    public boolean isSingleton() {
        return true;
    }
    
    @Override
    public T getObject() throws Exception {
        return SpringBootstrap.instance.rpcApp.getReferer(getId());
    }

    @Override
    public Class<?> getObjectType() {
    	try {
    		return Class.forName(getInterfaceName());
    	} catch(Exception e) {
    		throw new RuntimeException(e);
    	}
    }
}
