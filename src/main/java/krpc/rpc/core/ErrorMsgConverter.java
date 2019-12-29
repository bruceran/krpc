package krpc.rpc.core;

import krpc.common.Plugin;

import java.util.Map;

public interface ErrorMsgConverter extends Plugin {
    String getErrorMsg(int retCode);
    Map<String,String> getAllMsgs();
}

