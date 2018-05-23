package krpc.test.call;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;
import com.xxx.userservice.proto.UserServiceAsync;

import krpc.bootstrap.Bootstrap;
import krpc.bootstrap.ClientConfig;
import krpc.bootstrap.MonitorConfig;
import krpc.bootstrap.RpcApp;
import krpc.core.RpcClientContext;
public class RpcClientTest {

	static Logger log = LoggerFactory.getLogger(RpcClientTest.class);
	
	public static void main(String[] args) throws Exception {
		
		RpcApp app = new Bootstrap() 
				.addClient(new ClientConfig().setConnections(1))
				.addReferer("us",UserService.class,"127.0.0.1:5600") 
				.addReferer("usa",UserServiceAsync.class,"127.0.0.1:5600") 
				.setMonitorConfig(new MonitorConfig().setLogFormatter("simple").setMaskFields("password"))
				.build();
		
		app.initAndStart();

		// user code

		//Thread.sleep(2000);
		
		UserService us = app.getReferer("us");
		UserServiceAsync usa = app.getReferer("usa");

		LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
		LoginRes res = us.login(req);
		log.info("res="+res.getRetCode()+","+res.getRetMsg());

		//Thread.sleep(2000);
		
		UpdateProfileReq ureq = UpdateProfileReq.newBuilder().build();
		RpcClientContext.setTimeout(1000); // specify a timetout dynamically
		UpdateProfileRes ures = us.updateProfile(ureq);
		log.info("res="+ures.getRetCode()+","+ures.getRetMsg());

		//Thread.sleep(2000);
		
		CompletableFuture<LoginRes> f = usa.login(req);  // call async
		LoginRes resa = f.get();
		log.info("resa="+resa.getRetCode()+","+resa.getRetMsg());

		//Thread.sleep(2000);

		CompletableFuture<LoginRes> f2 = usa.login(req); 
		f2.thenAccept( (res0) -> {
							log.info("in listener, resa="+res0.getRetCode()+","+res0.getRetMsg() );
						}
			  ).thenRun( () -> 
				log.info("f2 received response")
						);

		LoginReq.Builder b = LoginReq.newBuilder().setUserName("abc").setPassword("mmm");
		
		LoginReq req1 = b.build();
		LoginReq req2 = b.build();
		LoginReq req3 = b.build();
		LoginReq req4 = b.build();
		LoginReq req5 = b.build();

		CompletableFuture<LoginRes> f11 = usa.login(req1);  // call async
		CompletableFuture<LoginRes> f12 = usa.login(req2);  // call async
		CompletableFuture<LoginRes> f13 = usa.login(req3);  // call async
		CompletableFuture<LoginRes> f14 = usa.login(req4);  // call async
		CompletableFuture<LoginRes> f15 = usa.login(req5);  // call async
		
		f11.get();
		f12.get();
		f13.get();
		f14.get();
		f15.get();
		
		log.info("res11="+f11.getNow(null).getRetCode()+","+f11.getNow(null).getRetMsg());
		log.info("res12="+f12.getNow(null).getRetCode()+","+f12.getNow(null).getRetMsg());
		log.info("res13="+f13.getNow(null).getRetCode()+","+f13.getNow(null).getRetMsg());
		log.info("res14="+f14.getNow(null).getRetCode()+","+f14.getNow(null).getRetMsg());
		log.info("res15="+f15.getNow(null).getRetCode()+","+f15.getNow(null).getRetMsg());
		
		for(int i=0;i<5;++i) {
			LoginReq reqx = b.build();
			LoginRes resx = us.login(reqx);
		}
		// user code end
		
		Thread.sleep(120000);
		
		app.stopAndClose();
	}	
	
		
}

