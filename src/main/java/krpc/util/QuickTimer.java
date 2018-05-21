package krpc.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class QuickTimer {

	long expireTime;
	Object data;
	AtomicBoolean cancelled;

	public QuickTimer (long expireTime,Object data,AtomicBoolean cancelled) {
		this.expireTime = expireTime;
		this.data = data;
		this.cancelled = cancelled;
	}
	
    public void cancel() {
        cancelled.set(true);
    }
}


