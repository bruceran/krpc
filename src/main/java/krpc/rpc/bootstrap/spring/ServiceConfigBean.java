package krpc.rpc.bootstrap.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.core.env.Environment;

import krpc.rpc.bootstrap.ServiceConfig;

public class ServiceConfigBean extends ServiceConfig implements InitializingBean, BeanNameAware,  ApplicationContextAware,
		ApplicationListener<ApplicationEvent>  {

    public void setBeanName(String name) {
    	if( !name.startsWith(ServiceConfigBean.class.getName()) )
    		setId(name);
    }
    
    public void afterPropertiesSet() throws Exception {
    	
    	String group = getGroup();
    	if( group == null || group.isEmpty()) {
    		Environment environment = (Environment)SpringBootstrap.instance.spring.getBean("environment");
    		group = environment.getProperty("spring.profiles.active");
    		setGroup(group);
    	}
    	
    	String impl = this.getImpl() == null ? null : this.getImpl().toString()  ;
		Object bean = loadBean(impl,this.getInterfaceName(),SpringBootstrap.instance.spring);
		if( bean == null ) throw new RuntimeException("bean not found for service "+ this.getInterfaceName() );
		this.setImpl(bean);
		
        SpringBootstrap.instance.getBootstrap().addService(this);
    }
	
    Object loadBean(String impl, String interfaceName,BeanFactory beanFactory) {
    	if( interfaceName == null ) return null;
    	
    	String beanName;
		if( impl != null && !impl.isEmpty()) {
			beanName = impl;
		} else {
			int p = interfaceName.lastIndexOf(".");
			if( p < 0 ) return null;
			String name = interfaceName.substring(p+1);
			beanName = name.substring(0,1).toLowerCase()+name.substring(1);
		}
		try {
			Object o = beanFactory.getBean(beanName);
			return o; 
		} catch(Exception e1) {
			try {
				Object o = beanFactory.getBean(Class.forName(interfaceName));
				return o;
			} catch(Exception e2) {
				return null;
			}
		}
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
    
}
