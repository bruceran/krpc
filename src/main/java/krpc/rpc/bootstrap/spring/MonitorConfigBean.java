package krpc.rpc.bootstrap.spring;

import org.springframework.beans.factory.InitializingBean;

import krpc.rpc.bootstrap.MonitorConfig;

public class MonitorConfigBean extends MonitorConfig implements InitializingBean {
	
    public void afterPropertiesSet() throws Exception {
        SpringBootstrap.instance.bootstrap.setMonitorConfig(this);
    }

}
