package krpc.rpc.core;

public class ClientContext {

    static private ThreadLocal<ClientContextData> tlData = new ThreadLocal<>();
    static private ThreadLocal<String> tlConnId = new ThreadLocal<>();
    static private ThreadLocal<Integer> tlTimeout = new ThreadLocal<>();
    static private ThreadLocal<String> tlAttachment = new ThreadLocal<>();
    static private ThreadLocal<String> tlDyeing = new ThreadLocal<>();
    static private ThreadLocal<RetrierInfo> tlRetrier = new ThreadLocal<>(); // persist to disk and retry

    public static ClientContextData get() {
        return tlData.get();
    }

    public static void set(ClientContextData data) {
        tlData.set(data);
    }

    public static void remove() {
        tlData.remove();
    }

    // may be used for 2 cases:
    // 1:  reverse call, server -> client
    // 2:  stateful call, client -> server
    public static void setConnId(String connId) {
        tlConnId.set(connId);
    }

    public static String getConnId() {
        String s = tlConnId.get();
        return s;
    }

    public static String removeConnId() {
        String s = tlConnId.get();
        if (s != null)
            tlConnId.remove();
        return s;
    }

    public static void setTimeout(int timeout) {
        tlTimeout.set(timeout);
    }

    public static int getTimeout() {
        Integer i = tlTimeout.get();
        return i == null ? 0 : i;
    }

    public static int removeTimeout() {
        Integer i = tlTimeout.get();
        if (i != null)
            tlTimeout.remove();
        return i == null ? 0 : i;
    }

    public static void setAttachment(String attachment) {
        tlAttachment.set(attachment);
    }

    public static String getAttachment() {
        String s = tlAttachment.get();
        return s;
    }

    public static String removeAttachment() {
        String s = tlAttachment.get();
        if (s != null)
            tlAttachment.remove();
        return s;
    }

    static public class RetrierInfo {
        public int maxTimes = 0;
        public int[] waitSeconds = new int[] {1};
    }

    public static void setRetrier(int  maxTimes,int  ... waitSeconds) {
        RetrierInfo r = new RetrierInfo();
        r.maxTimes = maxTimes;
        if( waitSeconds != null && waitSeconds.length > 0 ) {
            r.waitSeconds = waitSeconds;
        }
        tlRetrier.set(r);
    }

    public static RetrierInfo removeRetrier() {
        RetrierInfo r = tlRetrier.get();
        if (r != null)
            tlRetrier.remove();
        return r;
    }

    public static void setDyeing(String dyeing) {
        tlDyeing.set(dyeing);
    }

    public static String getDyeing() {
        String s = tlDyeing.get();
        return s;
    }

    public static String removeDyeing() {
        String s = tlDyeing.get();
        if (s != null)
            tlDyeing.remove();
        return s;
    }



}
