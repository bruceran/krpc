package krpc.redis.reqres;

public class SetReq extends BaseKeyReq<SetReq> {

	String value;
	int ex;
	int px;
	boolean nx;
	boolean xx;
	
	public SetReq(String key,String value) {
		this.key = key;
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public int getEx() {
		return ex;
	}

	public void setEx(int ex) {
		this.ex = ex;
	}

	public int getPx() {
		return px;
	}

	public void setPx(int px) {
		this.px = px;
	}

	public boolean isNx() {
		return nx;
	}

	public void setNx(boolean nx) {
		this.nx = nx;
	}

	public boolean isXx() {
		return xx;
	}

	public void setXx(boolean xx) {
		this.xx = xx;
	}
	
}
