package krpc.rpc.bootstrap;

public class ApplicationConfig  {

	String name = "unknown";
	String dataDir = ".";
	
	String traceAdapter = "default"; // default, skywalking, zipkin, cat
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

}

