package krpc.rpc.web;

import java.util.Map;

//interface used only for WebPlugin
public interface WebRes extends WebMessage {
	
	int getHttpCode();
	WebRes setHttpCode(int httpCode);
	
	String getHeader(String name);
	WebRes setHeader(String name,String value);
	
	WebRes addCookie(String name,String value);  // value can be xxx or xxx^p1=v1^p2=v2^...

	String getCharSet();	

	String getContentType();
	WebRes setContentType(String contentType);
	String getContent();
	WebRes setContent(String content);
	
	Map<String, Object> getResults();
	WebRes setResults(Map<String, Object> results);

}
