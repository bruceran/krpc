package krpc.bootstrap.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStoppedEvent;

import krpc.bootstrap.ServiceConfig;

public class ServiceConfigBean extends ServiceConfig implements InitializingBean, BeanNameAware,  BeanFactoryAware,
		ApplicationListener<ApplicationEvent>  {

    public void setBeanName(String name) {
    	if( !name.startsWith(ServiceConfigBean.class.getName()) )
    		setId(name);
    }
    
    public void afterPropertiesSet() throws Exception {
        SpringBootstrap.instance.bootstrap.addService(this);
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
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		SpringBootstrap.instance.spring = beanFactory;
	}
    
}
