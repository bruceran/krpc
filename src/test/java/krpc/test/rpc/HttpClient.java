package krpc.test.rpc;

import krpc.httpclient.DefaultHttpClient;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;

public class HttpClient {

    public static void main(String[] args) throws Exception {

        test2();
    }

    static void test1() {
        DefaultHttpClient c = new DefaultHttpClient();
        c.init();
        //HttpClientReq req = new HttpClientReq("GET","http://127.0.0.1:9411/ui");
        //HttpClientReq req = new HttpClientReq("GET","http://localhost:9411/ui");
        HttpClientReq req = new HttpClientReq("GET", "http://192.168.213.128:9411/zipkin/");
        //HttpClientReq req = new HttpClientReq("POST","https://127.0.0.1:9999/user/test1").setContent("{\"userName\":\"abc\"}");
        //HttpClientReq req = new HttpClientReq("GET","https://www.sina.com.cn");
        req.setGzip(true);

        HttpClientRes res = c.call(req);
        System.out.println("retCode=" + res.getRetCode());
        System.out.println("httpCode=" + res.getHttpCode());
        System.out.println("contentType=" + res.getContentType());
        System.out.println("content=" + res.getContent());

        //Thread.sleep(5000);

        c.close();
    }

    static void test2() throws Exception {
        DefaultHttpClient c = new DefaultHttpClient();
        c.init();

        for (int i = 0; i < 10; ++i) {
            System.out.println("---------------------");
            //HttpClientReq req = new HttpClientReq("GET","http://www.baidu.com");
            //HttpClientReq req = new HttpClientReq("GET","http://www.sina.com.cn");
            HttpClientReq req = new HttpClientReq("GET", "http://www.163.com");
            //HttpClientReq req = new HttpClientReq("GET","http://192.168.213.128:9411/zipkin/");
            //HttpClientReq req = new HttpClientReq("POST","http://127.0.0.1:8888/user/test1").setContent("{\"userName\":\"abc\"}");
            req.setKeepAlive(true);
            req.setTimeout(3000);
            HttpClientRes res = c.call(req);
            System.out.println("retCode=" + res.getRetCode());
            //System.out.println("httpCode="+res.getHttpCode());
        }

        Thread.sleep(50000);

        c.close();
    }

}

