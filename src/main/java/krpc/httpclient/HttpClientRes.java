package krpc.httpclient;

import io.netty.handler.codec.http.HttpHeaders;

public class HttpClientRes {

	public static final int SUCCESS = 0;
	public static final int URL_PARSE_ERROR = -1;
	public static final int RES_PARSE_ERROR = -2;
	public static final int TIMEOUT_ERROR = -3;
	public static final int CONNECT_EXCEPTION = -4;
	public static final int INTERRUPTED = -5;
	
	private int retCode;
	
	private int httpCode = 200;
	private String contentType = "application/json";
	private String content;
	private HttpHeaders headers;
	
	public HttpClientRes(int retCode) {
		this.retCode = retCode;
	}

	public HttpClientRes(int httpCode,String content,HttpHeaders headers) {
		this.httpCode = httpCode;
		this.content = content;
		this.headers = headers;
	}
	
	void setHeaders(HttpHeaders headers) {
		this.headers = headers;
	}	
	
	public String getHeader(String name) {
		if( headers == null ) return null;
		return headers.get(name);
	}

	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}

	public int getRetCode() {
		return retCode;
	}

	public void setRetCode(int retCode) {
		this.retCode = retCode;
	}

	public int getHttpCode() {
		return httpCode;
	}

	public void setHttpCode(int httpCode) {
		this.httpCode = httpCode;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}


}

