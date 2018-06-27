package krpc.test.rpc;

import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.xxx.userservice.proto.HttpDownloadStaticRes;
import com.xxx.userservice.proto.HttpPluginTestReq;
import com.xxx.userservice.proto.HttpPluginTestRes;
import com.xxx.userservice.proto.HttpPluginTestService;
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

public class HttpServerTest {

	static Logger log = LoggerFactory.getLogger(HttpServerTest.class);
	
	public static void main(String[] args) throws Exception {
		
		UserServiceImpl2 impl = new UserServiceImpl2(); // user code is here
		HttpPluginTestService impl2 = new HttpPluginTestServiceImpl(); // user code is here

		RpcApp app = new Bootstrap()
			.addWebServer(8890) 
			// .addServer(5600) 
			.addService(UserService.class,impl) 
			.addService(HttpPluginTestService.class,impl2) 
			.build();
		
		app.initAndStart();
		
		Thread.sleep(12000000);

		app.stopAndClose();
		
		impl.t.interrupt();
		
	}	
		
}

class HttpPluginTestServiceImpl implements HttpPluginTestService {

	public HttpPluginTestRes test1(HttpPluginTestReq req) {
		HttpPluginTestRes.Builder builder = HttpPluginTestRes.newBuilder().setRetCode(0);
		builder.setEmail("test@a.com").setMobile("13100001111").setGender("mail");
		builder.setPlainText("abc").setRedirectUrl("http://www.baidu.com");
		return builder.build();
	}
	
	public HttpDownloadStaticRes test2(HttpPluginTestReq req) {
		HttpDownloadStaticRes.Builder builder = HttpDownloadStaticRes.newBuilder().setRetCode(0);
		//builder.setDownloadFile("c:\\ws\\site\\static\\hello.html");
		//builder.setDownloadFile("c:\\ws\\site\\static\\中文.html"); // existed file or generated file
		builder.setAttachment(1);
		//builder.setAutoDelete("true"); // used to delete generated file
		
		ByteString bs = ByteString.copyFrom("abc".getBytes());
		builder.setDownloadStream(bs);
		builder.setFilename("中文.html");
		
		return builder.build();		
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
		
		RpcContextData ctx = ServerContext.get();
		
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
				log.info("async updateProfile received req#"+i);
				//try { Thread.sleep(3000); } catch(Exception e) {}
				UpdateProfileRes res = UpdateProfileRes.newBuilder().setRetCode(-100002).build();
				c.done(res); // !!! call this anytime if you have get response
			}
		} catch(Exception e) {
		}
	}

}