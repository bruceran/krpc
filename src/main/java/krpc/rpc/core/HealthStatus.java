package krpc.rpc.core;

public class HealthStatus {
    private String type; // krpc, db, redis, mq,  ...
    private boolean status; // true means healthy
    private String reason; // unhealthy reason

    public HealthStatus() {
    }

    public HealthStatus(String type, boolean status, String reason) {
        this.type = type;
        this.status = status;
        this.reason = reason;
    }

    public HealthStatus(String type, boolean status, String reason,String target,String addrs) {
        this.type = type;
        this.status = status;
        reason = reason + " [target="+target+",addrs="+addrs+"]";
        this.reason = reason;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}