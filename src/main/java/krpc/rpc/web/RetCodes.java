package krpc.rpc.web;

public class RetCodes extends krpc.rpc.core.RetCodes {
	
	 static public final int HTTP_NOT_FOUND = -404;  
	 static public final int HTTP_METHOD_NOT_ALLOWED = -405;  
	 
	 static public final int HTTP_NO_LOGIN = -560;  
	 static public final int HTTP_NO_SESSIONSERVICE = -561;  
	 static public final int HTTP_CLIENT_NOT_FOUND = -562;  

	 static public String retCodeText(int retCode) {
		 switch(retCode) {

		 	case HTTP_NOT_FOUND: return "not found";
		 	case HTTP_METHOD_NOT_ALLOWED: return "method not allowed";
		 	case HTTP_NO_SESSIONSERVICE: return "session service not found";
		 	case HTTP_NO_LOGIN: return "not login yet";
		 	case HTTP_CLIENT_NOT_FOUND: return "service not found";
		 	
		 	default: return krpc.rpc.core.RetCodes.retCodeText(retCode);
		 }
	 }
}

