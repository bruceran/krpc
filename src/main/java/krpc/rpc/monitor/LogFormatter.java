package krpc.rpc.monitor;

import com.google.protobuf.Message;
import krpc.common.Plugin;
import krpc.rpc.web.WebMessage;

public interface LogFormatter extends Plugin {
    String toLogStr(Message body);

    String toLogStr(WebMessage body);
}
