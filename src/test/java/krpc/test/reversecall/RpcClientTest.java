package krpc.test.reversecall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.pushservice.proto.PushReq;
import com.xxx.pushservice.proto.PushRes;
import com.xxx.pushservice.proto.PushService;
import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UserService;

import krpc.bootstrap.Bootstrap;
import krpc.bootstrap.RpcApp;

public class RpcClientTest {

	static Logger log = LoggerFactory.getLogger(RpcClientTest.class);
	
	public static void main(String[] args) throws Exception {
		
		PushServiceImpl impl = new PushServiceImpl();
		
		RpcApp app = new Bootstrap() 
				.addReferer("us",UserService.class,"127.0.0.1:5600") 
				.addReverseService(PushService.class,impl)  // !!!
				.build();
		
		app.initAndStart();

		// user code
		
		UserService us = app.getReferer("us");

		LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
		LoginRes res = us.login(req);
		log.info("res="+res.getRetCode()+","+res.getRetMsg());

		// user code end
		
		Thread.sleep(5000);
		
		app.stopAndClose();
	}	

}

class PushServiceImpl implements PushService {

	static Logger log = LoggerFactory.getLogger(PushServiceImpl.class);
	
	public PushRes push(PushReq req) {
		log.debug("received:" + req.getMessage()+","+req.getClientId());
		return PushRes.newBuilder().setRetCode(0).setRetMsg("hello, I have recieved your push!").build();
	}
	
}

