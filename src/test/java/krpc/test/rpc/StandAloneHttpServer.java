package krpc.test.rpc;

import com.google.protobuf.ByteString;
import com.xxx.userservice.proto.*;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.WebServerConfig;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;

public class StandAloneHttpServer {

    static Logger log = LoggerFactory.getLogger(StandAloneHttpServer.class);

    public static void main(String[] args) throws Exception {

        UserServiceImpl2 impl = new UserServiceImpl2(); // user code is here
        HttpPluginTestService impl2 = new HttpPluginTestServiceImpl(); // user code is here

        RpcApp app = new Bootstrap()
                .addWebServer(new WebServerConfig(8890))
                 .addServer(7600)
                .addService(UserService.class, impl)
                .addService(HttpPluginTestService.class, impl2)
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

    public HttpUploadTestRes upload1(HttpUploadTestReq1 req) {

        System.out.println("a=" + req.getA());
        System.out.println("b=" + req.getB());

        if (req.getFilesCount() > 0) {
            for (UploadFile f : req.getFilesList()) {
                System.out.println("upload filename=" + f.getFilename());
                System.out.println("upload file=" + f.getFile());
                System.out.println("upload size=" + f.getSize());
                System.out.println("upload ext=" + f.getExt());
                System.out.println("upload contentType=" + f.getContentType());
                // new File(f.getFile()).delete();
            }
        }
        return HttpUploadTestRes.newBuilder().setRetCode(0).build();
    }

    public HttpUploadTestRes upload2(HttpUploadTestReq2 req) {

        System.out.println("a count=" + req.getACount());
        System.out.println("b=" + req.getB());

        UploadFile f = req.getFiles();
        if (f != null) {
            System.out.println("upload filename=" + f.getFilename());
            System.out.println("upload file=" + f.getFile());
            System.out.println("upload size=" + f.getSize());
            System.out.println("upload ext=" + f.getExt());
            System.out.println("upload contentType=" + f.getContentType());
        }

        return HttpUploadTestRes.newBuilder().setRetCode(0).build();
    }

}

class UserServiceImpl2 implements UserService {

    static Logger log = LoggerFactory.getLogger(UserServiceImpl2.class);

    int i = 0;
    ArrayBlockingQueue<RpcClosure> queue = new ArrayBlockingQueue<>(100);
    Thread t;

    UserServiceImpl2() {
        t = new Thread(() -> run());
        t.start();
    }

    public LoginRes login(LoginReq req) {

        RpcContextData ctx = ServerContext.get();

        log.info("login received, peers=" + ctx.getMeta().getTrace().getPeers());
        i++;
        return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#" + i).build();
    }
    public Login2Res login2(Login2Req req) {
        return Login2Res.ok();
    }
    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        i++;
        RpcClosure u = ServerContext.closure(req); // !!! you can pass this object anywhere
        queue.offer(u);
        return null;
    }

    public void run() {
        try {
            while (true) {
                RpcClosure c = queue.take();
                log.info("async updateProfile received req#" + i);
                //try { Thread.sleep(3000); } catch(Exception e) {}
                UpdateProfileRes res = UpdateProfileRes.newBuilder().setRetCode(-100002).build();
                c.done(res); // !!! call this anytime if you have get response
            }
        } catch (Exception e) {
        }
    }

}