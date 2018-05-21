package krpc.bootstrap.spring;

import org.springframework.beans.factory.InitializingBean;

import krpc.bootstrap.MonitorConfig;

public class MonitorConfigBean extends MonitorConfig implements InitializingBean {
	
    public void afterPropertiesSet() throws Exception {
        SpringBootstrap.instance.bootstrap.setMonitorConfig(this);
    }

}
