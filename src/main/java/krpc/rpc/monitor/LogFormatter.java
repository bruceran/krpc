package krpc.rpc.monitor;

import com.google.protobuf.Message;

import krpc.rpc.core.Plugin;
import krpc.rpc.web.WebMessage;

public interface LogFormatter extends Plugin {
    String toLogStr(boolean isServerLog, Message body);
    String toLogStr(boolean isServerLog, WebMessage body);
}
