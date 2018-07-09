package krpc.httpclient;

import io.netty.handler.codec.http.HttpHeaders;

public class HttpClientRes {

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

