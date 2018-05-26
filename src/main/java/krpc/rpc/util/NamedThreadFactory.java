package krpc.rpc.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory implements ThreadFactory {

	private String namePrefix;
	
	AtomicInteger threadNumber = new AtomicInteger(1);
	SecurityManager s = System.getSecurityManager();
	ThreadGroup group = s != null ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();

	public NamedThreadFactory(String namePrefix)  {
		this.namePrefix = namePrefix;
	}

    public Thread newThread(Runnable r){
    	Thread t = new Thread(group, r,  namePrefix + "-thread-" + threadNumber.getAndIncrement(), 0);
        if (t.isDaemon())
            t.setDaemon(false);
        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }

}

