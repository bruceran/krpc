package krpc.bootstrap.spring;

import org.springframework.beans.factory.BeanFactory;

import krpc.bootstrap.Bootstrap;
import krpc.bootstrap.RpcApp;

public class SpringBootstrap {
	static public final SpringBootstrap instance = new SpringBootstrap();
	
	public BeanFactory spring;
	
	boolean inited = false;
	boolean stopped = false;
	boolean closed = false;
	Bootstrap bootstrap = new Bootstrap(); // todo can be customized
	RpcApp rpcApp = null;
	
    public void build() {
    	if( !inited ) {
        	rpcApp = bootstrap.build();
        	rpcApp.initAndStart();
        	inited = true;       	
    	}
    }
    
	public void stop()  {
		if(!stopped) {
			rpcApp.stop();
			stopped = true;
		}
	}
	
	public void close()  {
		if(!closed) {
			rpcApp.close();
			closed = true;
			rpcApp = null;
			bootstrap = new Bootstrap();
		}
	}
    
}

