package krpc.test.rpc.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RpcServerTest {

	static Logger log = LoggerFactory.getLogger(RpcServerTest.class);
	
	public static void main(String[] args) throws Exception {
		
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring-server.xml");

		Thread.sleep(15000);
		context.stop();
		Thread.sleep(1000);
		
		context.close();
        ((ch.qos.logback.classic.Logger) log).getLoggerContext().stop();		
	}	
		
}

