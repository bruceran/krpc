package krpc.trace;

public class Metric {

	public final static int COUNT = 1;
	public final static int QUANTITY = 2;
	public final static int SUM = 3;
	public final static int QUANTITY_AND_SUM = 4;
	
	private String key;
	private int type;
	private String value;
	
	public Metric(String key,int type,String value) {
		this.key = key;
		this.type = type;
		this.value = value;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

}
