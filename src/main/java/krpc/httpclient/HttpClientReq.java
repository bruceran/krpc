package krpc.httpclient;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HttpClientReq {

	private int timeout = 3000;
	private boolean gzip = false;
	private boolean keepAlive = false;
	
	private String method = "GET";
	private String url;
	private String contentType = "application/json";
	private String content;
	private Map<String,String> headers;
	
	private URL urlObj = null;
	
	public HttpClientReq(String method,String url) {
		this.method = method;
		this.url = url;
	}

    String getPathQuery() {
    	URL u = getUrlObj();
    	String path = u.getPath();
    	String query = u.getQuery();
    	if( query == null ) return path;
    	return path + "?" + query;
    }
    
    URL getUrlObj() {
    	if( urlObj != null ) return urlObj;
    	
    	try {
    		urlObj = new URL(url);
	    	return urlObj;
    	} catch(Exception e) {
    		return null;
    	}
    }
    
	public HttpClientReq addHeader(String name,String value) {
		if( headers == null ) headers = new HashMap<>();
		headers.put(name, value);
		return this;
	}

	public int getTimeout() {
		return timeout;
	}
	public HttpClientReq setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}
	public String getMethod() {
		return method;
	}
	public HttpClientReq setMethod(String method) {
		this.method = method;
		return this;
	}
	public String getUrl() {
		return url;
	}
	public HttpClientReq setUrl(String url) {
		this.url = url;
		return this;
	}
	public String getContent() {
		return content;
	}
	public HttpClientReq setContent(String content) {
		this.content = content;
		return this;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}
	public HttpClientReq setHeaders(Map<String, String> headers) {
		this.headers = headers;
		return this;
	}

	public String getContentType() {
		return contentType;
	}

	public HttpClientReq setContentType(String contentType) {
		this.contentType = contentType;
		return this;
	}

	public boolean isGzip() {
		return gzip;
	}

	public HttpClientReq setGzip(boolean gzip) {
		this.gzip = gzip;
		return this;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public HttpClientReq setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}
	
}

