package krpc.rpc.web;

public class WebConstants {

    static public final String DefaultCharSet = "utf-8";

    static public final String TraceIdName = "x-trace-id";
    static public final String DyeingName = "x-dyeing";

    static public final String LoginFlagName = "loginFlag";
    static public final String SessionMapName = "session";
    static public final String SessionIdName = "sessionId";

    static public final String DefaultSessionIdCookieName = "JSESSIONID";

    static public final String HeaderPrefix = "header";
    static public final String CookiePrefix = "cookie";
    static public final String HttpCodeName = "httpCode";
    static public final String HttpContentTypeName = "httpContentType";

    static public final String MIMETYPE_JSON_NO_CHARSET = "application/json";
    static public final String MIMETYPE_JSON = "application/json; charset=utf-8";
    static public final String MIMETYPE_FORM = "application/x-www-form-urlencoded";

    static public final String ContentFormat = "{\"retCode\":%d,\"retMsg\":\"%s\"}";

    static public final String Server = "krpc webserver 1.0";

    static public final String DOWNLOAD_FILE_FIELD = "downloadFile";
    static public final String DOWNLOAD_FILE_RANGE_FIELD = "downloadFileRange";
    static public final String DOWNLOAD_EXPIRES_FIELD = "expires";
    static public final String DOWNLOAD_STREAM_FIELD = "downloadStream";
    static public final String NOT_EXISTED_FILE = "not_existed";
}
