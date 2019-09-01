package krpc.trace;

import krpc.rpc.core.proto.RpcMeta;

import java.util.Map;

public interface TraceContext {

    void startForServer(String type, String action);

    Span stopForServer(int retCode); // the server span ended
    Span stopForServer(int retCode,String retMsg); // the server span ended
    Span stopForServer(String result); // the server span ended

    void start(String type, String action);

    Span startAsync(String type, String action);

    void tagForRpc(String key, String value);

    void tagForRpcIfAbsent(String key, String value);

    String getTagForRpc(String key);

    String getTagsForRpc();

    Span currentSpan();

    RpcMeta.Trace getTrace();

    long getThreadId();

    String getThreadName();

    String getThreadGroupName();

    long getRequestTimeMicros();

    long getStartMicros();

    Map<String,String> getTagsMapForRpc();

} 
