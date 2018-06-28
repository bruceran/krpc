package krpc.rpc.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.protobuf.ByteString;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import krpc.rpc.core.Plugin;
import krpc.rpc.core.ReflectionUtils;

public class DefaultWebRes implements WebRes {

	HttpVersion version;
	boolean keepAlive;
	boolean isHeadMethod;
	
	int httpCode = 200;
	HttpHeaders headers;
	List<Cookie> cookies;
	String content;

	Map<String,Object> results;

	public DefaultWebRes(DefaultWebReq req,int httpCode) {
		this.version = req.getVersion();
		this.keepAlive = req.isKeepAlive();
		this.isHeadMethod = req.isHeadMethod();
		this.httpCode = httpCode;
	}
	
	public String getStringResult(String key) {
		if( results == null ) return null;
		Object o = results.get(key);
		if( o == null ) return null;
		if( (o instanceof String) ) return (String)o;
		return o.toString();
	}
	
	public ByteString getByteStringResult(String key) {
		if( results == null ) return null;
		Object o = results.get(key);
		if( o == null ) return null;
		if( (o instanceof ByteString ) ) return (ByteString)o;
		return null;
	}
	
	public String getCookie(String name) {
		if( cookies == null ) return null;
		for(Cookie c:cookies) {
			if( c.name().equals(name) ) return c.value();
		}
		return null;
	}
	public int getRetCode() {
		if( results == null ) return 0;
		Integer i = (Integer)results.get(ReflectionUtils.retCodeFieldInMap);
		if( i == null ) return 0;
		return i;
	}

	public DefaultWebRes setRetCode(int code) {
		if( results == null ) results = new HashMap<>();
		results.put(ReflectionUtils.retCodeFieldInMap,code);
		return this;
	}
	
	public String getRetMsg() {
		if( results == null ) return null;
		return (String)results.get(ReflectionUtils.retMsgFieldInMap);
	}

	public DefaultWebRes setRetMsg(String msg) {
		if( results == null ) results = new HashMap<>();
		results.put(ReflectionUtils.retMsgFieldInMap,msg);
		return this;
	}
	
	public String getHeader(String name) {
		if( headers == null ) return null;
		return headers.get(name);
	}
	
	public DefaultWebRes setHeader(String name, String value) {
		if( headers == null ) headers = new DefaultHttpHeaders();
		headers.set(name, value);
		return this;
	}
	
	public DefaultWebRes setContentType(String contentType) {
		if( headers == null ) headers = new DefaultHttpHeaders();
		headers.set(HttpHeaderNames.CONTENT_TYPE,contentType);
		return this;
	}
	
	public HttpHeaders getHeaders() {
		return headers;
	}

	public String getContentType() {
		if( headers == null ) return null;
		String contentTypeStr = headers.get(HttpHeaderNames.CONTENT_TYPE);
		if( contentTypeStr == null ) return null;
		int p = contentTypeStr.indexOf(";");
		return p >= 0 ?  contentTypeStr.substring(0,p) : contentTypeStr ;
	}

	public String getCharSet() {
		if( headers == null ) return null;
		String contentTypeStr = headers.get(HttpHeaderNames.CONTENT_TYPE);
		if( contentTypeStr == null ) return WebConstants.DefaultCharSet;
		int p = contentTypeStr.indexOf(";");
		return p >= 0 ? WebUtils.parseCharSet(contentTypeStr.substring(p+1)) : WebConstants.DefaultCharSet;
	}

	public DefaultWebRes addCookie(String name, String value) {

		String paramsStr = null;
		int p = value.indexOf("^");
		if( p >= 0 ) {
			paramsStr = value.substring(p+1);
			value = value.substring(0, p);
		} 
		
		if( cookies == null ) cookies = new ArrayList<Cookie>();
		Cookie c = new DefaultCookie(name,value);
		if( paramsStr != null) {
			Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
			if( params.containsKey("domain") )
				c.setDomain(params.get("domain"));
			if( params.containsKey("path") )
				c.setPath(params.get("path"));
			if( params.containsKey("maxAge") )
				c.setMaxAge(Long.parseLong(params.get("maxAge")));
			if( params.containsKey("httpOnly") )
				c.setHttpOnly(Boolean.parseBoolean(params.get("httpOnly")));
			else 
				c.setHttpOnly(true);
			if( params.containsKey("secure") )
				c.setSecure(Boolean.parseBoolean(params.get("secure")));
			if( params.containsKey("wrap") )
				c.setWrap(Boolean.parseBoolean(params.get("wrap")));
		}
		
		cookies.add(c);
		return this;
	}
	
	public List<Cookie> getCookies() {
		return cookies;
	}


	public DefaultWebRes setCookies(List<Cookie> cookies) {
		this.cookies = cookies;
		return this;
	}


	public HttpVersion getVersion() {
		return version;
	}

	public DefaultWebRes setVersion(HttpVersion version) {
		this.version = version;
		return this;
	}

	public int getHttpCode() {
		return httpCode;
	}

	public DefaultWebRes setHttpCode(int httpCode) {
		this.httpCode = httpCode;
		return this;
	}

	public String getContent() {
		return content;
	}

	public DefaultWebRes setContent(String content) {
		this.content = content;
		return this;
	}

	public Map<String, Object> getResults() {
		if( results == null ) results = new HashMap<>();
		return results;
	}

	public DefaultWebRes setResults(Map<String, Object> results) {
		this.results = results;
		return this;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public DefaultWebRes setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}


	public boolean isHeadMethod() {
		return isHeadMethod;
	}


	public void setHeadMethod(boolean isHeadMethod) {
		this.isHeadMethod = isHeadMethod;
	}

}
