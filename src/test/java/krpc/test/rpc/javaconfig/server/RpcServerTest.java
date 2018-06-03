package krpc.test.rpc.javaconfig.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class RpcServerTest {

	static Logger log = LoggerFactory.getLogger(RpcServerTest.class);
	
	public static void main(String[] args) throws Exception {
		
	    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyServerJavaConfig.class);

		Thread.sleep(150000);

		context.close();
        ((ch.qos.logback.classic.Logger) log).getLoggerContext().stop();		
	}	
		
}

