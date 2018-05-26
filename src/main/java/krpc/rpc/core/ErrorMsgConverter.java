package krpc.rpc.core;

public interface ErrorMsgConverter extends Plugin {
    String getErrorMsg(int retCode);
}

