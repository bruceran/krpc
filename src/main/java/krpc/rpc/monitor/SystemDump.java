package krpc.rpc.monitor;

import java.lang.management.*;
import java.util.*;
import java.io.File;

import com.sun.management.OperatingSystemMXBean;

public class SystemDump {

    //  https://blog.csdn.net/MinFrog/article/details/54015901

    public static void dumpSystemProperties(Map<String,Object> values) {

        Runtime r = Runtime.getRuntime();
        Properties props = System.getProperties();
        Map<String, String> env = System.getenv();

        values.put("system.userName",env.get("USERNAME"));
        values.put("system.computerName",env.get("COMPUTERNAME"));

        values.put("system.java.version",props.getProperty("java.version"));
        values.put("system.java.vendor",props.getProperty("java.vendor"));
        values.put("system.os.name",props.getProperty("os.name"));
        values.put("system.os.arch",props.getProperty("os.arch"));
        values.put("system.os.version",props.getProperty("os.version"));

        File[] disks = File.listRoots();
        if( disks != null ) {
            for(File file : disks) {
                values.put("system.fileSystem["+file.getPath()+"].free", file.getFreeSpace() / 1024 / 1024 + "M" );
                values.put("system.fileSystem["+file.getPath()+"].used", (file.getTotalSpace() - file.getFreeSpace() ) / 1024 / 1024 + "M" );
                values.put("system.fileSystem["+file.getPath()+"].total", file.getTotalSpace() / 1024 / 1024 + "M" );
            }
        }

        values.put("system.hardware.availableProcessors",r.availableProcessors());

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        values.put("system.jvm.threadCount",threadMXBean.getThreadCount());
        values.put("system.jvm.peakThreadCount",threadMXBean.getPeakThreadCount());
        values.put("system.jvm.daemonThreadCount",threadMXBean.getDaemonThreadCount());

        List<String> list = new ArrayList<>();
        long[] threadIds = threadMXBean.getAllThreadIds();
        for(long threadId: threadIds ) {
            ThreadInfo ti = threadMXBean.getThreadInfo(threadId);
            list.add(ti.getThreadName());
        }
        Collections.sort(list);
        values.put("system.jvm.threadNames",list);


        OperatingSystemMXBean osm = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        values.put("system.hardware.totalPhysicalMemorySize", osm.getTotalPhysicalMemorySize() / 1024 / 1024 + "MB" );
        values.put("system.hardware.freePhysicalMemorySize", osm.getFreePhysicalMemorySize() / 1024 / 1024 + "MB" );
        values.put("system.hardware.freeSwapSpaceSize", osm.getFreeSwapSpaceSize() / 1024 / 1024 + "MB" );

        values.put("system.jvm.totalMemory",r.totalMemory()/ 1024 / 1024 + "MB");
        values.put("system.jvm.freeMemory",r.freeMemory()/ 1024 / 1024 + "MB");
        values.put("system.jvm.maxMemory",r.maxMemory()/ 1024 / 1024 + "MB");

        MemoryMXBean memorymbean = ManagementFactory.getMemoryMXBean();
        values.put("system.jvm.heapMemoryUsage",poolUsageToString(memorymbean.getHeapMemoryUsage()) );
        values.put("system.jvm.nonHeapMemoryUsage",poolUsageToString(memorymbean.getNonHeapMemoryUsage()) );

        List<MemoryPoolMXBean> mpmList=ManagementFactory.getMemoryPoolMXBeans();
        for(MemoryPoolMXBean mpm:mpmList){
            values.put("system.pool["+mpm.getName()+"]", poolUsageToString(mpm.getUsage())  );
        }

        List<GarbageCollectorMXBean> gcmList=ManagementFactory.getGarbageCollectorMXBeans();
        for(GarbageCollectorMXBean gcm:gcmList){
            values.put("system.gc["+gcm.getName()+"].count", gcm.getCollectionCount() );
            values.put("system.gc["+gcm.getName()+"].time", gcm.getCollectionTime() + "ms" );
            values.put("system.gc["+gcm.getName()+"].pools", arrayToString(gcm.getMemoryPoolNames()));
        }
    }

    static String poolUsageToString(MemoryUsage usage) {
        StringBuilder b = new StringBuilder();
        b.append("init:"+usage.getInit() / 1024 / 1024 + "MB,");
        b.append("used:"+usage.getUsed() / 1024 / 1024 + "MB,");
        b.append("commited:"+usage.getCommitted() / 1024 / 1024 + "MB,");
        b.append("max:"+usage.getMax() / 1024 / 1024 + "MB");
        return b.toString();
    }

    static String arrayToString(String[] names) {
        StringBuilder b = new StringBuilder();
        for(String name: names) {
            if( b.length() > 0 ) b.append(",");
            b.append(name);
        }
        return b.toString();
    }
}
