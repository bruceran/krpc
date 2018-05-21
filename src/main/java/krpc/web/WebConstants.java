package krpc.web;

public class WebConstants {

	static public final String DefaultCharSet = "utf-8";
	
	static public final String TraceIdName = "X-Trace-Id";
	
	static public final String LoginFlagName = "loginFlag";
	static public final String SessionMapName = "session";
	static public final String SessionIdName = "sessionId";
	
	static public final String DefaultSessionIdCookieName = "JSESSIONID";
	
	static public final String HeaderPrefix = "header";
	static public final String CookiePrefix = "cookie";
	static public final String HttpCodeName = "httpCode";
	static public final String HttpContentTypeName = "httpContentType";

	static public final String MIMETYPE_JSON = "application/json";
	static public final String MIMETYPE_FORM = "application/x-www-form-urlencoded";

	static public final String ContentFormat = "{\"retCode\":%d,\"retMsg\":\"%s\"}";	
	
	//static public final String ContentType = "content-type";
	//static public final String ContentLength = "content-length";
	static public final String Server = "krpc webserver/1.0";
	
	static public String toHeaderName(String s) {
		StringBuilder b = new StringBuilder();
		for(int i=0;i<s.length();++i) {
			char ch = s.charAt(i);
			if( i > 0 && ch >= 'A' && ch <= 'Z' ) 
				b.append("-").append(ch);
			else
				b.append(ch);
		}
		return b.toString();
	}

	static public String parseCharSet(String s) {
		s = s.toLowerCase();
		int p = s.indexOf("charset=");
		if( p >= 0 ) {
			p += 8;
			int p2 = s.indexOf(";",p);
			if( p2 >= 0 )
				return s.substring(p,p2);
			else
				return s.substring(p);
		} else {
			return WebConstants.DefaultCharSet;
		}
	}

}
