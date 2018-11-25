package krpc.trace;

import krpc.rpc.core.proto.RpcMeta;

public interface TraceContext {

    public void startForServer(String type, String action);

    public Span stopForServer(String result); // the server span ended

    public void start(String type, String action);

    public Span startAsync(String type, String action);

    public void tagForRpc(String key, String value);

    public void tagForRpcIfAbsent(String key, String value);

    public String getTagForRpc(String key);

    public String getTagsForRpc();

    public Span currentSpan();

    public RpcMeta.Trace getTrace();

    public long getThreadId();

    public String getThreadName();

    public String getThreadGroupName();

    public long getRequestTimeMicros();

    public long getStartMicros();

} 
