package krpc.test.misc;

import java.net.URL;

import org.junit.Test;

import krpc.httpclient.DefaultHttpClient;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;

public class HttpClientTest {

	//@Test
	public void test1() throws Exception {
		
		DefaultHttpClient c = new DefaultHttpClient();
		c.init();
		HttpClientReq req = new HttpClientReq("GET","http://127.0.0.1:9411/ui");
		//HttpClientReq req = new HttpClientReq("GET","http://localhost:9411/ui");
		//HttpClientReq req = new HttpClientReq("GET","http://news.sina.com.cn/");
		
		HttpClientRes res = c.call(req);
		System.out.println("retCode="+res.getRetCode());
		System.out.println("httpCode="+res.getHttpCode());
		System.out.println("contentType="+res.getContentType());
		System.out.println("content="+res.getContent());
		
		Thread.sleep(5000);
		
		c.close();
	}


}

