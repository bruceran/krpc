package krpc.trace;

import krpc.rpc.core.proto.RpcMeta;

import java.util.Map;

public interface TraceContext {

    Span startForServer(String type, String action);
    Span stopForServer(int retCode); // the server span ended
    Span stopForServer(int retCode,String retMsg); // the server span ended

    Span rootSpan();

    Span startAsync(String type, String action);
    Span appendSpan(String type, String action,long startMicros, String status, long timeUsedMicros);
    Span start(String type, String action);

    void tagForRpc(String key, String value);

    void tagForRpcIfAbsent(String key, String value);

    String getTagForRpc(String key);

    String getTagsForRpc();

    RpcMeta.Trace getTrace();

    long getThreadId();

    String getThreadName();

    String getThreadGroupName();

    long getRequestTimeMicros();

    long getStartMicros();

    Map<String,String> getTagsMapForRpc();

    TraceContext detach();
} 
