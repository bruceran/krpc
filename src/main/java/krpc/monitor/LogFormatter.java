package krpc.monitor;

import com.google.protobuf.Message;

import krpc.core.Plugin;
import krpc.web.WebMessage;

public interface LogFormatter extends Plugin {
    String toLogStr(boolean isReqLog, Message body);
    String toLogStr(boolean isReqLog, WebMessage body);
}
