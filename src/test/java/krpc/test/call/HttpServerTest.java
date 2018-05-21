package krpc.test.call;

import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;

import krpc.bootstrap.Bootstrap;
import krpc.bootstrap.RpcApp;
import krpc.core.RpcClosure;
import krpc.core.RpcContextData;
import krpc.core.RpcServerContext;

public class HttpServerTest {

	static Logger log = LoggerFactory.getLogger(HttpServerTest.class);
	
	public static void main(String[] args) throws Exception {
		
		UserServiceImpl2 impl = new UserServiceImpl2(); // user code is here

		RpcApp app = new Bootstrap()
			.addWebServer(8888) 
			.addServer(5600) 
			.addService(UserService.class,impl) 
			.build();
		
		app.initAndStart();
		
		Thread.sleep(120000);

		app.stopAndClose();
		
		impl.t.interrupt();
		
	}	
		
}

class UserServiceImpl2 implements UserService {
	
	static Logger log = LoggerFactory.getLogger(UserServiceImpl2.class);
	
	int i = 0;
	ArrayBlockingQueue<RpcClosure> queue = new ArrayBlockingQueue<>(100);
	Thread t;
	
	UserServiceImpl2() {
		t = new Thread( ()->run() );
		t.start();
	}
	
	public LoginRes login(LoginReq req) {
		
		RpcContextData ctx = RpcServerContext.get();
		
		log.info("login received, peers="+ctx.getMeta().getPeers());
		i++;
		return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#"+i).build();
	}
	
	public UpdateProfileRes updateProfile(UpdateProfileReq req) {
		i++;
		RpcClosure u = RpcServerContext.newClosure(req); // !!! you can pass this object anywhere
		queue.offer(u);
		return null;
	}
	
	public void run() {
		try {
			while( true ) {
				RpcClosure c = queue.take();
				log.info("async updateProfile received req#"+i);
				//try { Thread.sleep(3000); } catch(Exception e) {}
				UpdateProfileRes res = UpdateProfileRes.newBuilder().setRetCode(-100002).build();
				c.done(res); // !!! call this anytime if you have get response
			}
		} catch(Exception e) {
		}
	}

}