package krpc.rpc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class QuickTimerEngine {

    static Logger log = LoggerFactory.getLogger(QuickTimerEngine.class);

    static AtomicInteger count = new AtomicInteger(1);

    int checkInterval = 100;

    HashMap<Integer, Queue<QuickTimer>> queueMap = new HashMap<Integer, Queue<QuickTimer>>();
    ConcurrentLinkedQueue<WaitingData> waitingList = new ConcurrentLinkedQueue<WaitingData>();

    AtomicBoolean shutdown = new AtomicBoolean();
    AtomicBoolean shutdownFinished = new AtomicBoolean();

    QuickTimerCallback callback;

    Thread thread = new Thread(new Runnable() {
        public void run() {
            service();
        }
    });

    public QuickTimerEngine(QuickTimerCallback callback) {
        this.callback = callback;
    }

    public void init() {
        thread.setName("krpc_quicktimer_" + QuickTimerEngine.count.getAndIncrement());
        thread.start();
    }

    public void close() {

        shutdown.set(true);
        thread.interrupt();
        while (!shutdownFinished.get()) {
            try {
                Thread.sleep(15);
            } catch (Exception e) {
            }
        }

    }

    public QuickTimer newTimer(int timeout, Object data) {
        long expireTime = System.currentTimeMillis() + timeout;
        QuickTimer timer = new QuickTimer(expireTime, data, new AtomicBoolean());
        WaitingData tp = new WaitingData(timeout, timer);
        waitingList.offer(tp);
        return timer;
    }

    void checkTimeout() {

        while (!waitingList.isEmpty()) {

            WaitingData wd = waitingList.poll();
            int timeout = wd.timeout;
            QuickTimer timer = wd.timer;

            Queue<QuickTimer> queue = queueMap.get(timeout);
            if (queue == null) {
                queue = new LinkedList<QuickTimer>();
                queueMap.put(timeout, queue);
            }
            queue.offer(timer);
        }

        long now = System.currentTimeMillis();

        for (Queue<QuickTimer> queue : queueMap.values()) {

            boolean finished = false;
            while (!finished && !queue.isEmpty()) {
                QuickTimer first = queue.peek();

                if (first.cancelled.get()) {
                    queue.remove();
                } else if (first.expireTime <= now) {

                    try {
                        callback.timeout(first.data);
                    } catch (Exception e) {
                        log.error("timer callback function exception e={}", e.getMessage());
                    }

                    queue.remove();
                } else {
                    finished = true;
                }
            }

        }

    }

    void service() {

        while (!shutdown.get()) {

            try {
                checkTimeout();
            } catch (Exception e) {
                log.error("checkTimeout exception, e={}", e.getMessage());
            }

            try {
                Thread.sleep(checkInterval);
            } catch (Exception e) {
            }
        }

        shutdownFinished.set(true);
    }

    static class WaitingData {

        int timeout;
        QuickTimer timer;

        WaitingData(int timeout, QuickTimer timer) {
            this.timeout = timeout;
            this.timer = timer;
        }
    }

}

