package krpc.rpc.bootstrap.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

public class RefererFactory<T> implements FactoryBean<T> {

    static Logger log = LoggerFactory.getLogger(RefererFactory.class);

    private String id;
    private String interfaceName;

    public RefererFactory(String id, String interfaceName) {
        this.id = id;
        this.interfaceName = interfaceName;
    }

    @Override
    public T getObject() throws Exception {
        return SpringBootstrap.instance.getRpcApp().getReferer(id);
    }

    @Override
    public Class<?> getObjectType() {
        try {
            return Class.forName(interfaceName);
        } catch (Throwable e) {
            log.error("cannot found class: " + interfaceName);
            System.exit(-1);
            return null;
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
