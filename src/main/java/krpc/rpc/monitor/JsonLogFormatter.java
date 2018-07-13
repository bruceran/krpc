package krpc.rpc.monitor;

import com.google.protobuf.Message;
import krpc.common.Json;
import krpc.rpc.util.MessageToMap;
import krpc.rpc.web.WebMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonLogFormatter extends AbstractLogFormatter {

    static Logger log = LoggerFactory.getLogger(JsonLogFormatter.class);

    public void config(String paramsStr) {
        configInner(paramsStr);
    }

    public String toLogStr(Message body) {
        try {
            Map<String, Object> allLog = new HashMap<>();
            MessageToMap.parseMessage(body, allLog, printDefault, maxRepeatedSizeToLog);
            adjustLog(allLog);
            return Json.toJson(allLog);
        } catch (Exception e) {
            log.error("toLogStr exception, e=" + e.getMessage(), e);
            return "";
        }
    }

    public String toLogStr(WebMessage body) {
        try {
            Map<String, Object> allLog = getLogData(body, maxRepeatedSizeToLog);
            adjustLog(allLog);
            return Json.toJson(allLog);
        } catch (Exception e) {
            log.error("toLogStr exception, e=" + e.getMessage());
            return "";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void adjustLog(Map<String, Object> allLog) {
        for (Map.Entry<String, Object> entry : allLog.entrySet()) {

            String key = entry.getKey();
            Object v = entry.getValue();

            if (maskFieldsSet.contains(key)) {
                allLog.put(key, "***");
                continue;
            }

            if (v instanceof Map) {
                adjustLog((Map) v);
                continue;
            }

            if (v instanceof List) {
                List l = (List) v;
                for (Object no : l) {
                    if (no instanceof Map) {
                        adjustLog((Map) no);
                    }
                }
            }
        }
    }

}
