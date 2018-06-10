package krpc.rpc.bootstrap.spring;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;

import krpc.rpc.bootstrap.RegistryConfig;

public class RegistryConfigBean extends RegistryConfig  implements InitializingBean, BeanNameAware {
	
    public void setBeanName(String name) {
    	if( !name.startsWith(RegistryConfigBean.class.getName()) )
    		setId(name);
    }
    
    public void afterPropertiesSet() throws Exception {
        SpringBootstrap.instance.getBootstrap().addRegistry(this);
    } 

}
