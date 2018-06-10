package krpc.rpc.bootstrap.spring;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;

import krpc.rpc.bootstrap.WebServerConfig;

public class WebServerConfigBean extends WebServerConfig  implements InitializingBean, BeanNameAware {
	
    public void setBeanName(String name) {
    	if( !name.startsWith(WebServerConfigBean.class.getName()) )
    		setId(name);
    }
    
    public void afterPropertiesSet() throws Exception {
        SpringBootstrap.instance.getBootstrap().addWebServer(this);
    } 

}
