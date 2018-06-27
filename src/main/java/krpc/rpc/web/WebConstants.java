package krpc.rpc.web;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import krpc.rpc.util.CryptHelper;

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

	static public final String Server = "krpc webserver 1.0";
	
	static private final String CHARSET_TAG = "charset=";
	
	static public final String DOWNLOAD_FILE_FIELD = "downloadFile";
	static public final String DOWNLOAD_FILE_RANGE_FIELD = "downloadFileRange";
	static public final String DOWNLOAD_EXPIRES_FIELD = "expires";
	static public final String DOWNLOAD_STREAM_FIELD = "downloadStream";
	

    final static String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    final static String HTTP_DATE_GMT_TIMEZONE = "GMT";
    
    final static ThreadLocal<SimpleDateFormat> df_tl = new ThreadLocal<SimpleDateFormat>() {
        public  SimpleDateFormat initialValue() {
        	SimpleDateFormat df = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            df.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
            return df;
        }
    };

    static public Date parseDate(String ifModifiedSince) {
    	try {
    		return df_tl.get().parse(ifModifiedSince);
    	} catch(Exception e) {
    		return null;
    	}
    }
    static public String formatDate(Date dt) {
		return df_tl.get().format(dt);
    }
        
    static public String generateEtag(File file) {
		long lastModified = file.lastModified();
		long size = file.length();
		String s = lastModified+":"+size;
		String etag = "\""+CryptHelper.md5(s) + "\"";
		return etag;
	}	

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
		int p = s.indexOf(CHARSET_TAG);
		if( p >= 0 ) {
			p += CHARSET_TAG.length();
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
