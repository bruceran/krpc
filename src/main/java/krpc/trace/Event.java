package krpc.trace;

public class Event {

    private String type;
    private String action;
    private String status;
    private String data;

    private long startMicros = System.nanoTime() / 1000;

    public Event(String type, String action, String status, String data) {
        this.type = type;
        this.action = action;
        this.status = status;
        this.data = data;
    }

    public String toAnnotationString() {
        String s = type + ":" + action + ":" + status;
        if (data != null) s += ":" + data;
        return s;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getStartMicros() {
        return startMicros;
    }

}
