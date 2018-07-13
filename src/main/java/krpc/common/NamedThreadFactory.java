package krpc.common;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {

    private String namePrefix;

    private AtomicInteger threadNumber = new AtomicInteger(1);
    private SecurityManager s = System.getSecurityManager();
    private ThreadGroup group = s != null ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();

    public NamedThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r, namePrefix + "-thread-" + threadNumber.getAndIncrement(), 0);
        if (t.isDaemon())
            t.setDaemon(false);
        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }

}

