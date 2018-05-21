package krpc.monitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import krpc.core.Plugin;
import krpc.core.ReflectionUtils;
import krpc.web.DefaultWebReq;
import krpc.web.DefaultWebRes;
import krpc.web.WebConstants;
import krpc.web.WebMessage;

abstract class BaseFormatter implements LogFormatter {

	String maskFields;
	int maxRepeatedSizeToLog = 1;
	boolean printDefault = true;

	HashSet<String> maskFieldsSet = new HashSet<String>();

	void configInner(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		maskFields = params.get("maskFields");
		if (!isEmpty(maskFields)) {

			String[] ss = maskFields.split(",");

			for (String s : ss) {
				if (s.trim().length() > 0)
					maskFieldsSet.add(s.trim());
			}
		}

		String s = params.get("maxRepeatedSizeToLog");
		if (!isEmpty(s))
			maxRepeatedSizeToLog = Integer.parseInt(s);

		s = params.get("printDefault");
		if (!isEmpty(s))
			printDefault = Boolean.parseBoolean(s);
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty() ;
	}

	public static Map<String,Object> getLogData(boolean isReqLog, WebMessage body,int maxRepeatedSizeToLog) {
		HashMap<String,Object> allLog = new HashMap<String,Object>();
		
		if( body instanceof DefaultWebReq ) {
			DefaultWebReq req = (DefaultWebReq)body;
			allLog.putAll(req.getParameters());
			adjustMap(allLog,maxRepeatedSizeToLog);
			allLog.put("httpHost", req.getHost());
			allLog.put("httpMethod", req.getMethodString());
			allLog.put("httpPath", req.getPath());
			String sessionId = req.getCookie(WebConstants.DefaultSessionIdCookieName);
			if( sessionId != null )
				allLog.put(WebConstants.SessionIdName, sessionId);
		}
		else if( body instanceof DefaultWebRes ) {
			DefaultWebRes res = (DefaultWebRes)body;
			allLog.putAll(res.getResults());
			adjustMap(allLog,maxRepeatedSizeToLog);			
			allLog.put("httpCode", res.getHttpCode());
			String sessionId = res.getCookie(WebConstants.DefaultSessionIdCookieName);
			if( sessionId != null )
				allLog.put(WebConstants.SessionIdName, sessionId);		
			allLog.remove(ReflectionUtils.retCodeFieldInMap);
		}
		return allLog;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })	
	static void adjustMap(Map<String,Object> allLog,int maxRepeatedSizeToLog) {
		for(String key: allLog.keySet()) {
			
			Object v = allLog.get(key);
			if( v instanceof Map ) {
				adjustMap((Map)v,maxRepeatedSizeToLog);
				continue;
			}
			
			if( v instanceof List ) {
				List l = (List)v;
				for(Object no : l) {
					if( no instanceof Map ) {
						adjustMap((Map)no,maxRepeatedSizeToLog);
					}
				}
			}
		}
	}

}
