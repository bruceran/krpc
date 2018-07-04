package krpc.rpc.core;

import krpc.common.Plugin;

public interface ErrorMsgConverter extends Plugin {
    String getErrorMsg(int retCode);
}

