package krpc.persistqueue.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import krpc.persistqueue.PersistQueue;
import krpc.persistqueue.PersistQueueManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistQueueManagerImpl implements PersistQueueManager {

    private static final Logger logger = LoggerFactory.getLogger(PersistQueueManagerImpl.class);

    private static final long PURGE_WAIT_TIME = 30 * 60 * 1000L;

    private ConcurrentHashMap<String, PersistQueue> map = new ConcurrentHashMap<String, PersistQueue>();
    private List<String> names = new ArrayList<String>();
    
    private static int timerCount = 1;
    private String dataDir;
    private Timer t;
    private int cacheSize = 100;

    public void init() throws IOException {

        File df = new File(dataDir);
        if (!df.exists()) {
            df.mkdirs();
        }

        // check completed first
        File[] files = new File(dataDir).listFiles();
        if( files == null ) {
            throw new RuntimeException("data dir cannot be created, dataDir="+dataDir);
        }

        for (File f : files) {
            String name = f.getName();
            if (!name.startsWith("queue_")) {
                continue;
            }
            int p = name.indexOf(".data.");
            if (p < 0) {
                continue;
            }

            if (PersistQueueImpl.checkCompetedOnOpen(f.getAbsolutePath())) {
                f.delete();
                logger.info("data file dropped, filename=" + f.getAbsolutePath());
            }
        }

        // open queue
        files = new File(dataDir).listFiles();
        for (File f : files) {
            String name = f.getName();
            if (!name.startsWith("queue_")) {
                continue;
            }
            int p = name.indexOf(".data.");
            if (p < 0) {
                continue;
            }

            String queueName = name.substring(6, p);
            getQueue(queueName);
        }

        t = new Timer("krpc_persistmanager_timer-"+timerCount);
        timerCount++;
        t.schedule(new TimerTask() {

            @Override
            public void run() {
                try {
                    purge();
                } catch(Throwable e) {
                    logger.error("purge exception",e);
                }
            }

        }, PURGE_WAIT_TIME, PURGE_WAIT_TIME);

        logger.info("PersistQueueManagerImpl started");
    }

    public void close() {

        t.cancel();

        Iterator<PersistQueue> it = map.values().iterator();
        while (it.hasNext()) {
            PersistQueue q = it.next();
            q.close();
        }

        logger.info("PersistQueueManagerImpl closed");
    }

    public synchronized void purge() {
        
        ArrayList<String> removeList = new ArrayList<String>();
        Iterator<PersistQueue> it = map.values().iterator();
        while (it.hasNext()) {
            
            PersistQueueImpl q = (PersistQueueImpl) it.next();
            
            if( q.isClosed() ) {
                removeList.add(q.getQueueName());
                continue;
            }
            
            try {
                q.purge();
            } catch (IOException e) {
                logger.error("queue {} purge failed", q.getQueueName());
            }
        }
        
        for(String s: removeList) {
            map.remove(s);
            purgeClosedQueueFiles(s);
        }
    }

    public void purgeClosedQueueFiles(String queueName) {
        
        File[] files = new File(dataDir).listFiles();
        for (File f : files) {
            String name = f.getName();
            if (!name.startsWith("queue_"+queueName)) {
                continue;
            }
            int p = name.indexOf(".data.");
            if (p < 0) {
                continue;
            }

            if (PersistQueueImpl.checkCompetedOnOpen(f.getAbsolutePath())) {
                f.delete();
                logger.info("data file dropped, filename=" + f.getAbsolutePath());
            }
        }        
    }
    
    public List<String> getQueueNames() {
        return names;
    }

    public synchronized void close(String name) {
        
        PersistQueue queue = map.get(name);
        if (queue != null) {
            queue.close();
        }

        logger.info("queue {} closed", name);
        
        File[] files = new File(dataDir).listFiles();
        for (File f : files) {

            if (!f.getName().startsWith("queue_"+name+".data.")) {
                continue;
            }

            if (PersistQueueImpl.checkCompetedOnOpen(f.getAbsolutePath())) {
                f.delete();
                logger.info("data file dropped, filename=" + f.getAbsolutePath());
            }

        }
        logger.info("queue {} removed", name);
    }
    
    public synchronized PersistQueue getQueue(String name) throws IOException {

        PersistQueue queue = map.get(name);
        if (queue != null) {
            return queue;
        }
        PersistQueueImpl q = new PersistQueueImpl(dataDir, name, cacheSize);
        try {
            q.init();
        } catch (IOException e) {
            q.close();
            logger.error("queue {} init failed", name);
            throw e;
        }
        map.put(name, q);
        names.add(name);
        return q;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }
    

}
