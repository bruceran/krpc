package krpc.bootstrap.spring;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;

import krpc.bootstrap.RegistryConfig;

public class RegistryConfigBean extends RegistryConfig  implements InitializingBean, BeanNameAware {
	
    public void setBeanName(String name) {
    	if( !name.startsWith(RegistryConfigBean.class.getName()) )
    		setId(name);
    }
    
    public void afterPropertiesSet() throws Exception {
        SpringBootstrap.instance.bootstrap.addRegistry(this);
    } 

}
