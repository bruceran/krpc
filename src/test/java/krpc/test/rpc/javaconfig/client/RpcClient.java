package krpc.test.rpc.javaconfig.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UserService;

import krpc.rpc.bootstrap.RpcApp;

public class RpcClient {

	static Logger log = LoggerFactory.getLogger(RpcClient.class);
	
	public static void main(String[] args) throws Exception {
		
	    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyClientJavaConfig.class);

	    RpcApp rpcApp = (RpcApp)context.getBean("rpcApp");
	    rpcApp.start();
	    
		// user code
		
		UserService us = (UserService)context.getBean("userService");

		LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
		LoginRes res = us.login(req);
		log.info("res="+res.getRetCode()+","+res.getRetMsg());

		// user code end
		
		Thread.sleep(5000);

		rpcApp.stop();
		
		context.close();
        ((ch.qos.logback.classic.Logger) log).getLoggerContext().stop();	
	}	

}



