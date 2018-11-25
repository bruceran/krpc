package krpc.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyAlarm implements Alarm {

    static Logger log = LoggerFactory.getLogger(DummyAlarm.class);

    public String getAlarmId(String type) {
        return "999" + type;
    }

    public void alarm(String type, String msg) {
        log.error("alarm message received, alarmId={},msg={}",getAlarmId(type),msg);
    }

}
