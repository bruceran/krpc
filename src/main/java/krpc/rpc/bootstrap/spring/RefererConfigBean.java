package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.RefererConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

public class RefererConfigBean extends RefererConfig implements InitializingBean, ApplicationContextAware {

    public RefererConfigBean() {
        SpringBootstrap.instance.getBootstrap().addReferer(this);
    }

    @Override
    public void setApplicationContext(ApplicationContext beanFactory) throws BeansException {
        SpringBootstrap.instance.spring = (ConfigurableApplicationContext) beanFactory;
    }

    public void afterPropertiesSet() throws Exception {
        String group = getGroup();
        if (group == null || group.isEmpty()) {
            Environment environment = (Environment) SpringBootstrap.instance.spring.getBean("environment");
            String profileGroup = environment.getProperty("spring.profiles.active");
            String forceGroup = environment.getProperty("krpc.registry.group");
            if( forceGroup != null ) {
                profileGroup = forceGroup;
            }
            setGroup(profileGroup);
        }
    }

}
