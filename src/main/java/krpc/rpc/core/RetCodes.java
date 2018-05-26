package krpc.rpc.core;

public class RetCodes {
	
	 // client side error
	 static public final int RPC_TIMEOUT = -450;  
	 static public final int NO_CONNECTION = -451;
	 static public final int SEND_FAILED = -452;  
	 static public final int CONNECTION_BROKEN = -453;
	 static public final int USER_CANCEL = -454;
	 static public final int EXEC_EXCEPTION = -455; // only used in future
	 static public final int REFERER_NOT_ALLOWED = -456;
	 static public final int ENCODE_REQ_ERROR = -457;
	 static public final int DECODE_RES_ERROR = -458;

	 // server side error
	 static public final int BUSINESS_ERROR = -500;
	 static public final int SERVER_SHUTDOWN = -503;
	 static public final int QUEUE_FULL = -550;
	 static public final int QUEUE_TIMEOUT = -551;
	 static public final int DECODE_REQ_ERROR = -552;
	 static public final int ENCODE_RES_ERROR = -553;
	 static public final int NOT_FOUND = -554;
	 static public final int FLOW_LIMIT = -555;
	 static public final int SERVICE_NOT_ALLOWED = -556;
	 static public final int SERVER_CONNECTION_BROKEN = -557; // just for server log, not returned to client
	 
	 static public boolean isTimeout(int retCode) {
		 return retCode == RPC_TIMEOUT || retCode == QUEUE_TIMEOUT;
	 }
	 
	 static public boolean hasExecuted(int retCode) {
		 return retCode == BUSINESS_ERROR || retCode <= -600;
	 }

	 static public boolean canSafeRetry(int retCode) {
		 return retCode == QUEUE_FULL || retCode == SERVER_SHUTDOWN || retCode == FLOW_LIMIT;
	 }
	 
	 static public boolean canRetryTimeout(int retCode) {
		 return retCode == QUEUE_TIMEOUT;
	 }
	 
	 static public String retCodeText(int retCode) {
		 switch(retCode) {
		 	case 0: return "";

		 	case NO_CONNECTION: return "no connection";
		 	case SEND_FAILED: return "failed to send to network";
		 	case CONNECTION_BROKEN: return "connection is reset";
		 	case RPC_TIMEOUT: return "rpc timeout";
		 	case USER_CANCEL: return "user cancelled";
		 	case EXEC_EXCEPTION: return "exception in future";
		 	case REFERER_NOT_ALLOWED: return "serviceId is not allowed";
		 	case ENCODE_REQ_ERROR: return "encode req error";
		 	case DECODE_RES_ERROR: return "decode res error";
		 	
		 	case BUSINESS_ERROR: return "business exception";
		 	case SERVER_SHUTDOWN: return "server shutdown";
		 	case FLOW_LIMIT: return "flow control limit exceeded";
		 	case QUEUE_FULL: return "queue is full";
		 	case QUEUE_TIMEOUT: return "timeout in queue";
		 	case DECODE_REQ_ERROR: return "decode req error";
		 	case ENCODE_RES_ERROR: return "encode res error";
		 	case NOT_FOUND: return "service or method not found";
		 	case SERVICE_NOT_ALLOWED: return "serviceId is not allowed";
		 	
		 	default: return "unknown error:"+retCode;
		 }
	 }
}

