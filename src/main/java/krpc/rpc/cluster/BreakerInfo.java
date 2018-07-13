package krpc.rpc.cluster;

public class BreakerInfo {

    private boolean enabled = false;
    private int windowSeconds = 5;
    private int windowMinReqs = 20;
    private int closeBy = 1; // 1=errorRate 2=timeoutRate
    private int closeRate = 50; // 50% in 5 seconds to close the addr
    private int sleepMillis = 5000;
    private int succMills = 500;
    private boolean forceClose = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getCloseRate() {
        return closeRate;
    }

    public void setCloseRate(int closeRate) {
        this.closeRate = closeRate;
    }

    public int getCloseBy() {
        return closeBy;
    }

    public void setCloseBy(int closeBy) {
        this.closeBy = closeBy;
    }

    public int getSuccMills() {
        return succMills;
    }

    public void setSuccMills(int succMills) {
        this.succMills = succMills;
    }

    public int getSleepMillis() {
        return sleepMillis;
    }

    public void setSleepMillis(int sleepMillis) {
        this.sleepMillis = sleepMillis;
    }

    public int getWindowMinReqs() {
        return windowMinReqs;
    }

    public void setWindowMinReqs(int windowMinReqs) {
        this.windowMinReqs = windowMinReqs;
    }

    public boolean isForceClose() {
        return forceClose;
    }

    public void setForceClose(boolean forceClose) {
        this.forceClose = forceClose;
    }

}
