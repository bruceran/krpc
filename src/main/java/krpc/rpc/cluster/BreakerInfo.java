package krpc.rpc.cluster;

public class BreakerInfo {

	private boolean enabled = false ;
	private int windowSeconds = 5;
	private int closeBy = 1; // 1=errorRate 2=timeoutRate
	private int closeRate  = 50; // 50% in 5 seconds to close the addr
	private int waitMillis = 5000;
	private int succMills = 500;
	
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
	public int getWaitMillis() {
		return waitMillis;
	}
	public void setWaitMillis(int waitMillis) {
		this.waitMillis = waitMillis;
	}
	public int getSuccMills() {
		return succMills;
	}
	public void setSuccMills(int succMills) {
		this.succMills = succMills;
	}
	
}
