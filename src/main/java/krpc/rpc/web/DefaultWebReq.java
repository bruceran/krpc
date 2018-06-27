package krpc.rpc.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import krpc.rpc.util.TypeSafe;

public class DefaultWebReq implements WebReq {

	HttpVersion version;
	HttpMethod method;
	boolean keepAlive;
	String path;
	String queryString;
	HttpHeaders headers;
	Map<String,String> cookies;
	String content;

	Map<String,Object> parameters;

	public boolean isHeadMethod() {
		return method == HttpMethod.HEAD;
	}
	
	public HttpHeaders getHeaders() {
		return headers;
	}
	
	public void freeMemory() {
		queryString = null;
		content = null;
	}
	
	public DefaultWebReq setHeaders(HttpHeaders headers) {
		this.headers = headers;
		return this;
	}

	public DefaultWebReq setHeader(String name,String value) {
		if( headers == null ) headers = new DefaultHttpHeaders();
		headers.set(name, value);	
		return this;
	}

	public DefaultWebReq setHost(String host) {
		if( headers == null ) headers = new DefaultHttpHeaders();
		headers.set(HttpHeaderNames.HOST,host);
		return this;
	}
	
	public DefaultWebReq setContentType(String contentType) {
		if( headers == null ) headers = new DefaultHttpHeaders();
		headers.set(HttpHeaderNames.CONTENT_TYPE,contentType);
		return this;
	}

	public String getHeader(String name) {
		if( headers == null ) return null;
		return headers.get(name);
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
		return p >= 0 ? WebConstants.parseCharSet(contentTypeStr.substring(p+1)) : WebConstants.DefaultCharSet;
	}

	public String getHost() {
		if( headers == null ) return null;
		return headers.get(HttpHeaderNames.HOST);
	}
	public String getHostNoPort() {
		if( headers == null ) return null;
		String host = headers.get(HttpHeaderNames.HOST);
		if( host == null ) return null;
		int p = host.indexOf(":");
		if( p >= 0 ) return host.substring(0, p);
		else return host;
	}	
	
	public String getXff() {
		if( headers == null ) return null;
		String xff = headers.get("x-forwarded-for");
	    if (xff != null && !xff.isEmpty() ) {
	        String[] ss = xff.split(",");
	        if (ss.length > 0) return ss[0].trim();
	    }
	    return null;
	}

	public boolean isHttps() {
		if( headers == null ) return false;
		String ht = headers.get("x-forwarded-proto");
	    if (ht == null) return false;
	    
	    switch(ht) {
	        case "http": return false;
	        case "https": return true;
	        default: return false;
	    }
	}

	public Map<String, Object> getParameters() {
		if( parameters == null ) parameters = new HashMap<String,Object>();
		return parameters;
	}

	@SuppressWarnings("unchecked")
	public DefaultWebReq addParameter(String name,Object value) {
		if( parameters == null ) parameters = new HashMap<String,Object>();
		
		Object o = parameters.get(name);
		if( o == null ) {
			parameters.put(name, value);
		} else if( o instanceof List ) {
			((List)o).add(value);
		} else {
			List l = new ArrayList();
			l.add(o);
			l.add(value);
			parameters.put(name, l);
		}
		return this;
	}

	public String getParameter(String name) {
		if( parameters == null ) return null;
		
		Object o = parameters.get(name);
		if( o == null ) return null;
	
		if( o instanceof String )
			return (String)o;
		
		String v = null;
		if( o instanceof List ) {
			List l = (List)o;
			if( l.size() == 0 ) {
				v = null;
			} else {
				v = TypeSafe.anyToString(l.get(0));
			}
		} else {
			v = TypeSafe.anyToString(o);
		}
		
		if( v != null ) {
			parameters.put(name, v);
		} else {
			parameters.remove(v);
		}
		return v;
	}

	public List<String> getParameterList(String name) {
		if( parameters == null ) return null;
		
		Object o = parameters.get(name);
		if( o == null ) return null;
		 
		if( o instanceof List )
			return (List)o;
		
		List<String> v = new ArrayList<String>();
		v.add( TypeSafe.anyToString(o) );
		parameters.put(name, v);
		return v;
	}

	public String getCookie(String name) {
		if( cookies == null ) cookies = decodeCookie();
		return cookies.get(name);
	}
    
	HashMap<String,String> decodeCookie() {
		String cookie = getHeader("cookie");
		if (cookie != null && !cookie.isEmpty() ) {
			Set<Cookie> decoded = ServerCookieDecoder.STRICT.decode(cookie);
	        if (decoded != null && decoded.size() > 0 ) {
	    		HashMap<String,String> m = new HashMap<String,String>();
	            for(Cookie c: decoded) {
	           		m.put(c.name(), c.value());
	            }
	            return m;
	        }
	    }
		return new HashMap<String,String>();
	}

	public HttpVersion getVersion() {
		return version;
	}
	public DefaultWebReq setVersion(HttpVersion version) {
		this.version = version;
		return this;
	}

	public String getMethodString() {
		return method.toString();
	}

	public HttpMethod getMethod() {
		return method;
	}
	public DefaultWebReq setMethod(HttpMethod method) {
		this.method = method;
		return this;
	}

	public String getQueryString() {
		return queryString;
	}
	public DefaultWebReq setQueryString(String queryString) {
		this.queryString = queryString;
		return this;
	}
	public String getContent() {
		return content;
	}
	public DefaultWebReq setContent(String content) {
		this.content = content;
		return this;
	}
	public String getPath() {
		return path;
	}
	public DefaultWebReq setPath(String path) {
		this.path = path;
		return this;
	}
	public boolean isKeepAlive() {
		return keepAlive;
	}

	public DefaultWebReq setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

}
