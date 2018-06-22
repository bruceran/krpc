package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.ApplicationConfig;

public class ApplicationConfigBean extends ApplicationConfig {
	
	private static int count = 0;

    public ApplicationConfigBean()   {
    	count++;
    	if( count >= 2 ) throw new RuntimeException("only one ApplicationConfigBean can be specified");
    	SpringBootstrap.instance.getBootstrap().setAppConfig(this);
    }

}
