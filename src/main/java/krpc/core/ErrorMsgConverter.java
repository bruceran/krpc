package krpc.core;

public interface ErrorMsgConverter extends Plugin {
    String getErrorMsg(int retCode);
}

