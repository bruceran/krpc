package krpc.core;

public class RpcClientContext {
	
	static ThreadLocal<RpcClientContextData> tlData = new ThreadLocal<RpcClientContextData>();
	static ThreadLocal<String> tlConnId = new ThreadLocal<String>();
	static ThreadLocal<String> tlAttachment = new ThreadLocal<String>();
	static ThreadLocal<Integer> tlTimeout = new ThreadLocal<Integer>();

    public static RpcClientContextData get() {
        return tlData.get();
    }

    public static void set(RpcClientContextData data) {
    	tlData.set(data);
    }	

    public static void remove() {
        tlData.remove();
    }    
    
    // setConnId is used only in reverse call: server -> client
    public static void setConnId(String connId) {
    	tlConnId.set(connId);
    }	   

    public static String getConnId() {
    	String s = (String)tlConnId.get();
        return s;
    }	
    
    public static String removeConnId() {
        String s = (String)tlConnId.get();
        if( s != null)
        	tlConnId.remove();
        return s;
    }
    
    public static void setTimeout(int timeout) {
    	tlTimeout.set(timeout);
    }	   

    public static int getTimeout() {
    	Integer i = (Integer)tlTimeout.get();
        return i == null ? 0 : i;
    }	    
    
    public static int removeTimeout() {
    	Integer i = (Integer)tlTimeout.get();
        if( i != null)
        	tlTimeout.remove();
        return i == null ? 0 : i;
    }	    

    public static void setAttachment(String attachment) {
    	tlAttachment.set(attachment);
    }	   

    public static String getAttachment() {
        String s = (String)tlAttachment.get();
        return s;
    }	    
    
    public static String removeAttachment() {
        String s = (String)tlAttachment.get();
        if( s != null)
        	tlAttachment.remove();
        return s;
    }
        
}
