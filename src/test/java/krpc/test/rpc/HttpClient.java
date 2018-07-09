package krpc.test.rpc;

import krpc.httpclient.DefaultHttpClient;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;

public class HttpClient {

	public static void main(String[] args) throws Exception {
		
		DefaultHttpClient c = new DefaultHttpClient();
		c.init();
		//HttpClientReq req = new HttpClientReq("GET","http://127.0.0.1:9411/ui");
		//HttpClientReq req = new HttpClientReq("GET","http://localhost:9411/ui");
		//HttpClientReq req = new HttpClientReq("GET","http://news.sina.com.cn/");
		HttpClientReq req = new HttpClientReq("POST","http://127.0.0.1:8888/user/test1").setContent("{\"userName\":\"abc\"}");
		//HttpClientReq req = new HttpClientReq("POST","http://127.0.0.1:9999/user/test1").setContent("{\"userName\":\"abc\"}");
		req.setMinSizeToGzip(1);
		req.setGzip(true);
		HttpClientRes res = c.call(req);
		System.out.println("retCode="+res.getRetCode());
		System.out.println("httpCode="+res.getHttpCode());
		System.out.println("contentType="+res.getContentType());
		System.out.println("content="+res.getContent());
		
		Thread.sleep(5000);
		
		c.close();
	}


}

