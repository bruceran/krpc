package krpc.rpc.bootstrap;

public class ApplicationConfig {

    int delayStart = 0; // -1=manually call start,  0=start after application context, n=start after n seconds
    String name = "unknown";
    String dataDir = ".";

    int sampleRate = 100;

    String traceAdapter = "default"; // default, zipkin, cat, skywalking
    String errorMsgConverter = "file";
    String fallbackPlugin = "default";
    String dynamicRoutePlugin;  // consul,etcd,zookeeper,jedis,...

    public ApplicationConfig() {
    }

    public ApplicationConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ApplicationConfig setName(String name) {
        this.name = name;
        return this;
    }

    public String getErrorMsgConverter() {
        return errorMsgConverter;
    }

    public ApplicationConfig setErrorMsgConverter(String errorMsgConverter) {
        this.errorMsgConverter = errorMsgConverter;
        return this;
    }

    public String getTraceAdapter() {
        return traceAdapter;
    }

    public ApplicationConfig setTraceAdapter(String traceAdapter) {
        this.traceAdapter = traceAdapter;
        return this;
    }

    public String getDataDir() {
        return dataDir;
    }

    public ApplicationConfig setDataDir(String dataDir) {
        this.dataDir = dataDir;
        return this;
    }

    public String getDynamicRoutePlugin() {
        return dynamicRoutePlugin;
    }

    public ApplicationConfig setDynamicRoutePlugin(String dynamicRoutePlugin) {
        this.dynamicRoutePlugin = dynamicRoutePlugin;
        return this;
    }

    public String getFallbackPlugin() {
        return fallbackPlugin;
    }

    public ApplicationConfig setFallbackPlugin(String fallbackPlugin) {
        this.fallbackPlugin = fallbackPlugin;
        return this;
    }

    public int getDelayStart() {
        return delayStart;
    }

    public ApplicationConfig setDelayStart(int delayStart) {
        this.delayStart = delayStart;
        return this;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public ApplicationConfig setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

}

