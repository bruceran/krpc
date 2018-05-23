package krpc.bootstrap;

public class ApplicationConfig  {

	String name = "default_app";
	String errorMsgConverter = "file";
	String flowControl = "";
	String mockFile = "";
	String traceIdGenerator = "zipkin"; // zipkin or eagle  // todo doc
	int sampleRate = 1; // todo doc  sample if hash(traceId) % sampleRate == 0, now only for webserver
	
	public ApplicationConfig() {
	}

	public ApplicationConfig(String name) {
		this.name = name;
	}

	public String getFlowControl() {
		return flowControl;
	}

	public ApplicationConfig setFlowControl(String flowControl) {
		this.flowControl = flowControl;
		return this;
	}

	public String getMockFile() {
		return mockFile;
	}

	public ApplicationConfig setMockFile(String mockFile) {
		this.mockFile = mockFile;
		return this;
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

	public String getTraceIdGenerator() {
		return traceIdGenerator;
	}

	public ApplicationConfig setTraceIdGenerator(String traceIdGenerator) {
		this.traceIdGenerator = traceIdGenerator;
		return this;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
	}

}

