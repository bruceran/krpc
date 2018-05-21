package krpc.bootstrap;

public class ApplicationConfig  {

	String name = "default_app";
	String errorMsgConverter = "file";
	String flowControl = "";
	String mockFile = "";
	
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

}

