package krpc.test.rpc;

import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;
import krpc.trace.Trace;

public class RpcServerTest {

	static Logger log = LoggerFactory.getLogger(RpcServerTest.class);
	
	public static void main(String[] args) throws Exception {
		
		UserServiceImpl impl = new UserServiceImpl(); // user code is here

		RpcApp app = new Bootstrap() 
			.addService(UserService.class,impl) 
			//.setTraceAdapter("zipkin:server=127.0.0.1:9411")
			.setName("uss")
			.build();
		
		app.initAndStart();
		
		Thread.sleep(3000000);

		app.stopAndClose();
		
		impl.t.interrupt();
	}	
}

class UserServiceImpl implements UserService {
	
	static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
	
	int i = 0;
	ArrayBlockingQueue<RpcClosure> queue = new ArrayBlockingQueue<>(100);
	Thread t;
	
	UserServiceImpl() {
		t = new Thread( ()->run() );
		t.start();
	}
	
	public LoginRes login(LoginReq req) {
		
		RpcContextData ctx = ServerContext.get();

		Trace.start("DB", "queryUser");
		try { Thread.sleep(100); } catch(Exception e) {}
		Trace.logEvent("find a user", "hi");
		Trace.tag("secret", "xxx");
		Trace.stop();
		
		Trace.start("REDIS", "set");
		try { Thread.sleep(100); } catch(Exception e) {}
		Trace.setRemoteAddr("10.1.2.198:8909");
		Trace.tag("userId", "mmm");
		Trace.tag("userName", "nnn");
		Trace.stop();
		
		log.info("login received, peers="+ctx.getMeta().getPeers());
		i++;
		return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#"+i).build();
	}
	
	public UpdateProfileRes updateProfile(UpdateProfileReq req) {
		i++;
		RpcClosure u = ServerContext.closure(req); // !!! you can pass this object anywhere
		queue.offer(u);
		return null;
	}
	
	public void run() {
		try {
			while( true ) {
				RpcClosure c = queue.take();
				c.recoverContext();
				log.info("async updateProfile received req#"+i);
				//try { Thread.sleep(3000); } catch(Exception e) {}
				UpdateProfileRes res = UpdateProfileRes.newBuilder().setRetCode(-100002).build();
				c.done(res); // !!! call this anytime if you have get response
			}
		} catch(Exception e) {
		}
	}

}