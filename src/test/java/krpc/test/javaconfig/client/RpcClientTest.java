package krpc.test.javaconfig.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UserService;

public class RpcClientTest {

	static Logger log = LoggerFactory.getLogger(RpcClientTest.class);
	
	public static void main(String[] args) throws Exception {
		
	    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyClientJavaConfig.class);


		// user code
		
		UserService us = (UserService)context.getBean("userService");

		LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
		LoginRes res = us.login(req);
		log.info("res="+res.getRetCode()+","+res.getRetMsg());

		// user code end
		
		Thread.sleep(5000);

		context.close();
        ((ch.qos.logback.classic.Logger) log).getLoggerContext().stop();	
	}	

}



