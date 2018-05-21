package krpc.core;

public class RpcException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private int retCode;
	
	public RpcException(int retCode,String retMsg) {
		super(retMsg);
		this.retCode = retCode;
	}

	public int getRetCode() {
		return retCode;
	}
}
