package krpc.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RetCodes {

    // rpc client side error
    static public final int NO_CONNECTION = -600;
    static public final int CONNECTION_BROKEN = -601;
    static public final int RPC_TIMEOUT = -602;
    static public final int USER_CANCEL = -603;
    static public final int SEND_FAILED = -604;
    static public final int EXEC_EXCEPTION = -605; // only used in future
    static public final int REFERER_NOT_ALLOWED = -606;
    static public final int ENCODE_REQ_ERROR = -607;
    static public final int DECODE_RES_ERROR = -608;

    // rpc server side error
    static public final int BUSINESS_ERROR = -620;
    static public final int VALIDATE_ERROR = -621;
    static public final int SERVER_SHUTDOWN = -622;
    static public final int QUEUE_FULL = -623;
    static public final int QUEUE_TIMEOUT = -624;
    static public final int DECODE_REQ_ERROR = -625;
    static public final int ENCODE_RES_ERROR = -626;
    static public final int NOT_FOUND = -627;
    static public final int FLOW_LIMIT = -628;
    static public final int DIST_FLOW_LIMIT = -629;
    static public final int SERVICE_NOT_ALLOWED = -630;
    static public final int SERVER_CONNECTION_BROKEN = -631; // just for server log, not returned to client

    // krpc.httpclient component error
    static public final int HTTPCLIENT_NO_CONNECT = -640;
    static public final int HTTPCLIENT_CONNECTION_BROKEN = -641;
    static public final int HTTPCLIENT_TIMEOUT = -642;
    static public final int HTTPCLIENT_INTERRUPTED = -643;
    static public final int HTTPCLIENT_URL_PARSE_ERROR = -644;
    static public final int HTTPCLIENT_RES_PARSE_ERROR = -645;

    // http server error
    static public final int HTTP_FORBIDDEN = -660;
    static public final int HTTP_URL_NOT_FOUND = -661;
    static public final int HTTP_METHOD_NOT_ALLOWED = -662;
    static public final int HTTP_TOO_LARGE = -663;
    static public final int HTTP_NO_LOGIN = -664;
    static public final int HTTP_NO_SESSIONSERVICE = -665;
    static public final int HTTP_SERVICE_NOT_FOUND = -666;

    static public final int BIZ_NOT_IMPLEMENTED = -700;
    static public final int BIZ_DISCARDED = -701;
    static public final int BIZ_PARAM_ERROR = -702;

    static public final Set<Integer> systemErrorCodes = new HashSet<>(); // 用于其他框架扩展

    static public boolean isSystemError(int retCode) {

        if( retCode == 0 ) return false;

        int v = -1 * retCode;

        if( v >= 600 && v < 700 && retCode != VALIDATE_ERROR ) { // -621 pb参数校验规则校验失败  不算系统错误
            return true;
        }

        if( systemErrorCodes.contains(retCode) ) {
            return true;
        }

        return false;
    }

    static public boolean isSystemErrorV2(int retCode) {
        return retCode != 0 ; // 非0都算错误
    }

    static public boolean isTimeout(int retCode) {
        return retCode == RPC_TIMEOUT || retCode == QUEUE_TIMEOUT;
    }

    static public boolean canSafeRetry(int retCode) {
        switch (retCode) {
            case SERVER_SHUTDOWN:
            case QUEUE_FULL:
                return true;
            default:
                return false;
        }
    }

    static public boolean canRecover(int retCode) {
        switch (retCode) {
            case VALIDATE_ERROR:
            case BIZ_NOT_IMPLEMENTED:
            case BIZ_DISCARDED:
            case BIZ_PARAM_ERROR:
                return false;
            default:
                return true;
        }
    }

    static public String retCodeText(int retCode) {
        String msg = map.get(retCode);
        if (msg == null) return "unknown error: " + retCode;
        return msg;
    }

    static private final Map<Integer, String> map = new HashMap<>();

    static public void init() {
    } // used for static initialization

    static {
        map.put(0, "");

        map.put(NO_CONNECTION, "no connection");
        map.put(CONNECTION_BROKEN, "connection is reset");
        map.put(RPC_TIMEOUT, "rpc timeout");
        map.put(USER_CANCEL, "user cancelled");
        map.put(SEND_FAILED, "failed to send to network");
        map.put(EXEC_EXCEPTION, "exception in future");
        map.put(REFERER_NOT_ALLOWED, "serviceId is not allowed");
        map.put(ENCODE_REQ_ERROR, "encode req error");
        map.put(DECODE_RES_ERROR, "decode res error");

        map.put(BUSINESS_ERROR, "business exception");
        map.put(VALIDATE_ERROR, "validate error: ");
        map.put(SERVER_SHUTDOWN, "server shutdown");
        map.put(QUEUE_FULL, "queue is full");
        map.put(QUEUE_TIMEOUT, "timeout in queue");
        map.put(DECODE_REQ_ERROR, "decode req error");
        map.put(ENCODE_RES_ERROR, "encode res error");
        map.put(NOT_FOUND, "service or method not found");
        map.put(FLOW_LIMIT, "flow control limit exceeded");
        map.put(DIST_FLOW_LIMIT, "dist flow control limit exceeded");
        map.put(SERVICE_NOT_ALLOWED, "serviceId is not allowed");
        map.put(SERVER_CONNECTION_BROKEN, "server connection is broken");

        map.put(HTTPCLIENT_NO_CONNECT, "no connection");
        map.put(HTTPCLIENT_CONNECTION_BROKEN, "connection exception");
        map.put(HTTPCLIENT_TIMEOUT, "call timeout");
        map.put(HTTPCLIENT_INTERRUPTED, "call interrupted");
        map.put(HTTPCLIENT_URL_PARSE_ERROR, "url parse error");
        map.put(HTTPCLIENT_RES_PARSE_ERROR, "response content parse error");

        map.put(HTTP_FORBIDDEN, "forbidden");
        map.put(HTTP_URL_NOT_FOUND, "url not found");
        map.put(HTTP_TOO_LARGE, "request content too large");
        map.put(HTTP_METHOD_NOT_ALLOWED, "method not allowed");
        map.put(HTTP_NO_SESSIONSERVICE, "session service not found");
        map.put(HTTP_NO_LOGIN, "not login yet");
        map.put(HTTP_SERVICE_NOT_FOUND, "service not found");

    }

}

