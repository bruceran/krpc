package krpc.rpc.core;

public interface RpcRetrier   {

    boolean submit(int retCode, RpcRetryTask task);

}
