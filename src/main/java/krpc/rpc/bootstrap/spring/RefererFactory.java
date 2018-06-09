package krpc.rpc.bootstrap.spring;

import org.springframework.beans.factory.FactoryBean;

public class RefererFactory<T>  implements FactoryBean<T>  {

	private String id;
	private String interfaceName;
	
	public RefererFactory(String id,String interfaceName) {
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
    	} catch(Exception e) {
    		throw new RuntimeException(e);
    	}
    }

	@Override
	public boolean isSingleton() {
		return true;
	}
    
}
