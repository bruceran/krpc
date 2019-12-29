package krpc.rpc.core;

import krpc.common.RetCodes;

public class ValidateResult {

    static public ErrorMsgConverter errorMsgConverter;  // init by bootstrap

    ValidateFieldInfo fi;

    public ValidateResult() {
    }

    public ValidateResult(ValidateFieldInfo fi) {
        this.fi = fi;
    }

    public int getRetCode() {
        if( fi == null ) return 0;
        if( fi.vld.getRetCode() >= 0 ) return RetCodes.VALIDATE_ERROR;
        return fi.vld.getRetCode();
    }

    public String getRetMsg() {
        if( fi == null ) return "";

        if( !isEmpty(fi.vld.getRetMsg()) )
            return fi.vld.getRetMsg();

        int retCode = getRetCode();

        String retMsg = null;

        if( errorMsgConverter != null ) {
            retMsg = errorMsgConverter.getErrorMsg(retCode);
        }

        if( retMsg == null || retMsg.isEmpty() ) {
//            retMsg = RetCodes.retCodeText(retCode);
            retMsg = "validate error:";
        }

        if( retMsg.endsWith(":") ) {
            retMsg += " " +fi.field.getFullName(); // 把参数名加在最后
        } else if( retMsg.contains("%s") ) {
            retMsg = retMsg.replace( "%s", fi.field.getFullName()); // 格式化参数名
        }

        return retMsg;
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public String getFieldName() {
        if( fi == null ) return null;
        return fi.field.getFullName();
    }
}
