/*
 * Copyright (c) 2011 Shanda Corporation. All rights reserved.
 *
 * Created on 2011-12-19.
 */
package krpc.persistqueue.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import krpc.persistqueue.PersistQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistQueueImpl implements PersistQueue {

    private static final Logger logger = LoggerFactory.getLogger(PersistQueueImpl.class);

    private static final byte STATUS_EMPTY = 0;
    private static final byte STATUS_AVAILABLE = 1;
    private static final byte STATUS_BUSY = 2;
    private static final byte STATUS_COMMITED = 3;

    private static final int FULL_EXCEPTION = -1;
    private static final int EMPTY_EXCEPTION = -2;

    private static final int DIR_ENTRY_SIZE = 5;
    private static final int DIR_ENTRY_COUNT = 500000;
    private static final int DIR_SIZE = DIR_ENTRY_SIZE * DIR_ENTRY_COUNT;

    private static final int CACHE_SIZE = 100;

    private String dataDir;
    private String queueName;
    private ArrayList<MappedFile> fileList = new ArrayList<MappedFile>();

    private MappedFile readFile;
    private MappedFile writeFile;

    private Lock lock = new ReentrantLock();
    private Condition notEmpty = lock.newCondition();
    
    private int cacheSize = CACHE_SIZE;
    
    private volatile boolean closed = false;

    public PersistQueueImpl(String dataDir, String queueName, int cacheSize) {
        this.dataDir = dataDir;
        this.queueName = queueName;
        this.cacheSize = cacheSize;
    }

    public String getFileNamePrefix() {
        return "queue_" + queueName + ".data.";
    }

    public String getFileName(int fileno) {
        return getFileNamePrefix() + fileno;
    }

    public void init() throws IOException {

        String fileNamePrefix = getFileNamePrefix();
        ArrayList<Integer> filenoList = new ArrayList<Integer>();
        File[] files = new File(dataDir).listFiles();
        for (File f : files) {
            String name = f.getName();

            int p = name.indexOf(fileNamePrefix);
            if (p != 0) {
                continue;
            }

            String filenoString = name.substring(p + fileNamePrefix.length());
            int fileno = Integer.parseInt(filenoString);
            filenoList.add(fileno);
        }
        Collections.sort(filenoList);
        for (int fileno : filenoList) {
            MappedFile f = loadFile(fileno);
            if (f.isOpened()) {
                fileList.add(f);
            } else {
                f.drop();
                logger.info("data file dropped, filename=" + f.filename);
            }
        }

        if (fileList.size() == 0) {
            MappedFile f = createFile(1);
            fileList.add(f);
        }

        readFile = fileList.get(0);
        writeFile = fileList.get(fileList.size() - 1);

        logger.info("PersistQueueImpl {} started", queueName);
    }

    public boolean isClosed() {
        return closed;
    }
    
    public void close() {

        lock.lock();
        try {

            for (MappedFile f : fileList) {
                f.close();
            }
            closed = true;
        
        } finally {
            lock.unlock();
        }        

        logger.info("PersistQueueImpl {} closed", queueName);    
    }

    public MappedFile createFile(int fileno) throws IOException {
        MappedFile mappedFile = new MappedFile();
        mappedFile.fileno = fileno;
        mappedFile.filename = getFileName(fileno);
        try {
            mappedFile.create();
        } catch (IOException e) {
            mappedFile.close();
            throw e;
        }
        return mappedFile;
    }

    public MappedFile loadFile(int fileno) throws IOException {
        MappedFile mappedFile = new MappedFile();
        mappedFile.fileno = fileno;
        mappedFile.filename = getFileName(fileno);
        try {
            mappedFile.open();
        } catch (IOException e) {
            mappedFile.close();
            throw e;
        }
        return mappedFile;
    }

    public boolean empty() {

        lock.lock();
        try {

            for (MappedFile f : fileList) {
                if (!f.checkEmpty()) {
                    return false;
                }
            }

            return true;

        } finally {
            lock.unlock();
        }
        
    }
    
    public int size() {
        
        int total = 0;
        
        lock.lock();
        try {

            for (MappedFile f : fileList) {
                total += f.size();
            }

            return total;

        } finally {
            lock.unlock();
        }        
    }
    public int cacheSize() {
        
        int total = 0;
        
        lock.lock();
        try {

            for (MappedFile f : fileList) {
                total += f.cacheSize();
            }

            return total;

        } finally {
            lock.unlock();
        }        
    }    
    
    public void purge() throws IOException {

        if( closed ) {
            return;
        }
        
        lock.lock();
        try {

            if( closed ) {
                return;
            }
            
            while (readFile.readCompleted()) {
                MappedFile f = getReadFile(readFile.fileno + 1);
                if (f == null) {
                    f = createFile(writeFile.fileno + 1);
                    fileList.add(f);
                    writeFile = f;
                }
                readFile = f;
            }

            ArrayList<MappedFile> deleteList = new ArrayList<MappedFile>();

            for (MappedFile f : fileList) {

                if (f.fileno >= readFile.fileno) {
                    break;
                }

                if (f.checkCompeted()) {
                    deleteList.add(f);
                }

            }

            for (MappedFile f : deleteList) {
                fileList.remove(f);
                f.close();
                f.drop();
                logger.info("data file closed, filename=" + f.filename);
            }

        } finally {
            lock.unlock();
        }

    }

    public MappedFile getReadFile(int fileno) {

        if (readFile.fileno == fileno) {
            return readFile;
        }

        for (MappedFile f : fileList) {
            if (f.fileno == fileno) {
                return f;
            }
        }

        return null;
    }
    

    public void put(String s) throws IOException {
        putAndReturnIdx(s);
    }
    
    public long putAndReturnIdx(String s) throws IOException {
        
        if (s == null || s.equals("")) {
            return -1;
        }

        byte[] bs = null;
        try {
            bs = s.getBytes("UTF-8");
        } catch (Exception e) {
            return -1;
        }

        return putAndReturnIdx(bs);
    }

    public void put(byte[] bs) throws IOException {
        putAndReturnIdx(bs);
    }
    
    public long putAndReturnIdx(byte[] bs) throws IOException {
        
        if (bs == null || bs.length == 0) {
            return -1;
        }
        
        long ret = 0;
        
        lock.lock();
        try {

            int idx = writeFile.write(bs);
            if (idx == FULL_EXCEPTION) {
                MappedFile f = createFile(writeFile.fileno + 1);
                fileList.add(f);
                writeFile = f;
                idx = writeFile.write(bs);
            }

            ret = (long) (((long) writeFile.fileno) << 32) + idx;

            notEmpty.signal();
            
        } finally {
            lock.unlock();
        }        
        
        return ret;
    }


    public long get() throws IOException, InterruptedException {
        return get(-1);
    }

    public long get(long wait) throws IOException, InterruptedException {

        if (wait < -1) {
            wait = -1;
        }

        lock.lock();
        try {

            for (; ; ) {

                int idx = readFile.get();
                if (idx == FULL_EXCEPTION) {
                    MappedFile f = getReadFile(readFile.fileno + 1);
                    if (f == null) {
                        f = createFile(writeFile.fileno + 1);
                        fileList.add(f);
                        writeFile = f;
                    }
                    readFile = f;
                    continue;
                }

                if (idx == EMPTY_EXCEPTION) {

                    if (wait == 0) {
                        return -1;
                    }

                    if (wait == -1) {
                        notEmpty.await();
                        continue;
                    }
                    if (wait > 0) {

                        boolean ok = notEmpty.await(wait, TimeUnit.MILLISECONDS);
                        if (!ok) {
                            return -1;
                        }
                        continue;
                    }
                }

                return (long) (((long) readFile.fileno) << 32) + idx;
            }

        } finally {
            lock.unlock();
        }
    }

    public byte[] getBytes(long idx) {

        lock.lock();
        try {
            int fileno = (int) (idx >> 32);
            int fileIdx = (int) (idx & 0xFFFFFFFF);
            MappedFile f = getReadFile(fileno);
            if (f == null) {
                return null;
            }
            return f.item(fileIdx);
        } catch (IOException e) {
            return null;
        } finally {
            lock.unlock();
        }
    }

    public String getString(long idx) {

        byte[] bs = getBytes(idx);
        if (bs == null) {
            return null;
        }

        try {
            return new String(bs, "UTF-8");
        } catch (Exception e) {
            return null;
        }

    }

    public void commit(long idx) {
        lock.lock();
        try {
            int fileno = (int) (idx >> 32);
            int fileIdx = (int) (idx & 0xFFFFFFFF);
            MappedFile f = getReadFile(fileno);
            if (f == null) {
                return;
            }
            f.commit(fileIdx);
        } finally {
            lock.unlock();
        }
    }

    public void rollback(long idx) {
        lock.lock();
        try {
            int fileno = (int) (idx >> 32);
            int fileIdx = (int) (idx & 0xFFFFFFFF);
            MappedFile f = getReadFile(fileno);
            if (f == null) {
                return;
            }
            f.rollback(fileIdx);

            if( readFile != f ) {
                readFile = f;
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    class MappedFile {

        int fileno;
        String filename;
        RandomAccessFile raf;
        FileChannel fc;
        MappedByteBuffer mbb;

        int readPosition = -1;
        int writePosition = -1;

        ConcurrentHashMap<Integer, byte[]> msgMap = new ConcurrentHashMap<Integer, byte[]>();

        int cacheSize() {
            return msgMap.size();
        }        
        
        public int hashCode() {
            return filename.hashCode();
        }

        public boolean equals(Object b) {
            if (!(b instanceof MappedFile)) {
                return false;
            }

            return filename.equals(((MappedFile) b).filename);
        }

        boolean writeCompleted() {
            return writePosition == DIR_ENTRY_COUNT;
        }

        boolean readCompleted() {
            return readPosition == DIR_ENTRY_COUNT;
        }

        int get() {

            if (readCompleted()) {
                return FULL_EXCEPTION;
            }

            for (; ; ) {

                int idx = readPosition;
                if (readCompleted()) {
                    return FULL_EXCEPTION;
                }

                mbb.position(idx * DIR_ENTRY_SIZE);
                byte status = mbb.get();
                if (status == STATUS_EMPTY) {
                    return EMPTY_EXCEPTION;
                }
                if (status == STATUS_AVAILABLE) {
                    mbb.position(idx * DIR_ENTRY_SIZE);
                    mbb.put(STATUS_BUSY);
                    readPosition++;
                    return idx;
                }
                if (status == STATUS_BUSY) {
                    readPosition++;
                    continue;
                }
                if (status == STATUS_COMMITED) {
                    readPosition++;
                    continue;
                }

            }
        }

        byte[] item(int idx) throws IOException {

            if (idx < 0 || idx >= DIR_ENTRY_COUNT) {
                return null;
            }

            byte[] bs = null;
            
            if( cacheSize > 0 ) {
                bs = msgMap.get(idx);
                if (bs != null) {
                    msgMap.remove(idx);
                    return bs;
                }
            }
            
            mbb.position(idx * DIR_ENTRY_SIZE);
            mbb.get();
            int offset = mbb.getInt();

            try {

                raf.seek(offset);
                int length = raf.readShort();
                if( length == -1 ) {
                	length = raf.readInt();
                } else {
                	length = length & 0xffff;
                }
                
                bs = new byte[length];
                raf.read(bs);

            } catch (IOException e) {
                logger.error("item() exception in (" + dataDir + "/" + filename + ") e=" + e.getMessage());
                throw e;
            }

            return bs;
        }

        void commit(int idx) {

            if (idx < 0 || idx >= DIR_ENTRY_COUNT) {
                return;
            }

            mbb.position(idx * DIR_ENTRY_SIZE);
            mbb.put(STATUS_COMMITED);
        }

        void rollback(int idx) {

            if (idx < 0 || idx >= DIR_ENTRY_COUNT) {
                return;
            }

            mbb.position(idx * DIR_ENTRY_SIZE);
            mbb.put(STATUS_AVAILABLE);
            
            readPosition = idx;
        }        

        int write(byte[] bs) throws IOException {

            if (writeCompleted()) {
                return FULL_EXCEPTION;
            }

            int position = 0;
            try {

                position = (int) raf.length();
                raf.seek(position);
                if( bs.length >= 65535 ) {
                	raf.writeShort(-1);
                	raf.writeInt(bs.length);
                } else {
                	raf.writeShort(bs.length);
                }
                
                raf.write(bs);

            } catch (IOException e) {
                logger.error("write() exception in (" + dataDir + "/" + filename + ") e=" + e.getMessage());
                throw e;
            }

            mbb.position(writePosition * DIR_ENTRY_SIZE);
            mbb.put(STATUS_AVAILABLE);
            mbb.putInt(position);

            if( cacheSize > 0 ) {
                if (msgMap.size() < cacheSize) {
                    msgMap.put(writePosition, bs);
                }
            }
            
            int lastWritePosition = writePosition;
            
            writePosition++;

            return lastWritePosition;
        }

        void create() throws IOException {

            try {
                raf = new RandomAccessFile(dataDir + "/" + filename, "rw");
                fc = raf.getChannel();
                mbb = fc.map(FileChannel.MapMode.READ_WRITE, 0, DIR_SIZE);
            } catch (IOException e) {
                logger.error("cannot create file(" + dataDir + "/" + filename + ") e=" + e.getMessage());
                throw e;
            }

            for (int i = 0; i < DIR_ENTRY_COUNT; ++i) {
                mbb.position(i * DIR_ENTRY_SIZE);
                mbb.put(STATUS_EMPTY);
            }

            readPosition = 0;
            writePosition = 0;
        }

        void drop() {
            new File(dataDir + "/" + filename).delete();
        }

        boolean checkCompeted() {

            int i = 0;
            for (; i < DIR_ENTRY_COUNT; ++i) {
                mbb.position(i * DIR_ENTRY_SIZE);
                byte status = mbb.get();
                if (status != STATUS_COMMITED) {
                    return false;
                }
            }

            return true;
        }

        boolean checkEmpty() {

            int i = 0;
            for (; i < DIR_ENTRY_COUNT; ++i) {
                mbb.position(i * DIR_ENTRY_SIZE);
                byte status = mbb.get();
                if (status != STATUS_COMMITED && status != STATUS_EMPTY ) {
                    return false;
                }
            }

            return true;
        }
        
        int size() {
            
            int total = 0;
            
            int i = 0;
            for (; i < DIR_ENTRY_COUNT; ++i) {
                mbb.position(i * DIR_ENTRY_SIZE);
                byte status = mbb.get();
                if (status != STATUS_COMMITED && status != STATUS_EMPTY ) {
                    total++;
                }
            }

            return total;
        }

        boolean isOpened() {
            return raf != null;
        }

        void open() throws IOException {

            try {
                raf = new RandomAccessFile(dataDir + "/" + filename, "rw");
                fc = raf.getChannel();
                mbb = fc.map(FileChannel.MapMode.READ_WRITE, 0, DIR_SIZE);
            } catch (IOException e) {
                logger.error("cannot open file(" + dataDir + "/" + filename + ") e=" + e.getMessage());
                throw e;
            }

            int i = 0;
            for (; i < DIR_ENTRY_COUNT; ++i) {
                mbb.position(i * DIR_ENTRY_SIZE);
                byte status = mbb.get();

                if (status == STATUS_BUSY) { // 恢复到STATUS_AVAILABLE
                    mbb.position(i * DIR_ENTRY_SIZE);
                    mbb.put(STATUS_AVAILABLE);
                }

            }

            i = 0;
            for (; i < DIR_ENTRY_COUNT; ++i) {
                mbb.position(i * DIR_ENTRY_SIZE);
                byte status = mbb.get();
                if (status == STATUS_EMPTY) {
                    break;
                }
            }

            writePosition = i;

            i = 0;
            for (; i < DIR_ENTRY_COUNT; ++i) {
                mbb.position(i * DIR_ENTRY_SIZE);
                byte status = mbb.get();

                if (status == STATUS_EMPTY || status == STATUS_AVAILABLE) {
                    break;
                }
            }

            readPosition = i;

        }

        void close() {

            if (mbb != null) {
                try {
                    UnMapTool.unmap(mbb);
                } catch (Exception e) {
                }
            }

            if (fc != null) {
                try {
                    fc.close();
                    fc = null;
                } catch (Exception e) {
                    logger.error("cannot close fc (" + filename + ")", e);
                }
            }

            if (raf != null) {
                try {
                    raf.close();
                    raf = null;
                } catch (Exception e) {
                    logger.error("cannot close raf (" + filename + ")", e);
                }
            }

        }
    }

    public static boolean checkCompetedOnOpen(String filename) {

        RandomAccessFile raf = null;

        try {
            raf = new RandomAccessFile(filename, "rw");

            byte[] bs = new byte[DIR_SIZE];

            try {
                raf.read(bs);
            } catch (Exception e) {
                return false;
            }

            int i = 0;
            for (; i < DIR_ENTRY_COUNT; ++i) {
                ;
                byte status = bs[i * DIR_ENTRY_SIZE];
                if (status == STATUS_BUSY) {
                    status = STATUS_AVAILABLE;
                }

                if (status == STATUS_AVAILABLE) {
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            return false;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                    raf = null;
                } catch (Exception e) {
                    logger.error("cannot close raf (" + filename + ")", e);
                }
            }
        }
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }
}

class UnMapTool {

    private static final Logger logger = LoggerFactory.getLogger(UnMapTool.class);

    private static Method GetCleanerMethod;
    private static Method cleanMethod;

    static {
        try {
            if (GetCleanerMethod == null) {
                Method getCleanerMethod = Class.forName("java.nio.DirectByteBuffer").getMethod("cleaner", new Class[0]);
                getCleanerMethod.setAccessible(true);
                GetCleanerMethod = getCleanerMethod;
                cleanMethod = Class.forName("sun.misc.Cleaner").getMethod("clean", new Class[0]);
            }
        } catch (Exception e) {
            GetCleanerMethod = null;
            cleanMethod = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void unmap(final MappedByteBuffer buffer) {

        if (buffer == null) {
            return;
        }
        if (GetCleanerMethod == null) {
            return;
        }
        if (cleanMethod == null) {
            return;
        }

        synchronized (buffer) {
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    try {
                        Object cleaner = GetCleanerMethod.invoke(buffer, new Object[0]);
                        cleanMethod.invoke(cleaner, new Object[0]);
                    } catch (Exception e) {
                        logger.error("unmap MappedByteBuffer error", e);
                    }
                    return null;
                }
            });
        }
    }

}
