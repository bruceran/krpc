package krpc.bootstrap.spring;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;

import krpc.bootstrap.ServerConfig;

public class ServerConfigBean extends ServerConfig  implements InitializingBean, BeanNameAware {
	
    public void setBeanName(String name) {
    	if( !name.startsWith(ServerConfigBean.class.getName()) )
    		setId(name);
    }
    
    public void afterPropertiesSet() throws Exception {
        SpringBootstrap.instance.bootstrap.addServer(this);
    } 

}
