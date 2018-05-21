
# 日志文件种类

    可通过logback.xml配置调整生成的日志
    
    日志文件共4种：
      作为rpcserver收到的日志，输出在req.log日志文件中
      作为rpcclient发出的调用日志，输出在call.log日志文件中
      作为webserver收到的日志，输出在web.log日志文件中
      每分钟输出一次3类日志的统计，输出在stats.log日志文件中

# 日志文件格式

## req.log/call.log/web.log 日志格式目前是统一的:

    文件分割符为: 一个逗号3个空格

    示例：
        2018-05-19 19:44:01.563,   
        127.0.0.1:62157:5463c892,   
        1,   
        8c859500563a478d88dbed531e221413,   
        1,   
        100,   
        1,   
        UserService.login,   
        0,   
        7997,   
        userName:abc^password:mmm,   
        retMsg:hello  friend. receive req#1

    格式：
        TIMESTAMP  消息的处理结束时间戳
        CONN_ID  连接标识, 包含对端IP+对端端口+连接ID
        SEQUENCE 消息号，用于客户端服务端收发包时排查问题
        TRACE_ID 全链路跟踪标识
        SPAN_INFO 全链路跟踪所需的parent spanid, spanid
        SERVICE_ID  服务号
        MSG_ID   消息号
        SERVICENAME 服务名+消息名
        RET_CODE, 错误码
        DURATION  耗时，到微秒
        REQ_BODY  请求参数, 以^作为分隔符,以:作为参数名和参数值之间的分隔符
        RES_BODY  响应参数, 格式同上

    请求参数和响应参数格式可配置
        默认采用simple格式: 对多级嵌套的消息有输出限制, 输出快
        可配置为json格式: 日志量可能增大很多, 输出日志更耗时

## stats.log

    文件分割符为: 一个逗号3个空格

    示例：
        2018-05-21 07:53:00,   webstats,   
        100,   1,   
        2,   0,   0,   
        1,   0,   0,   0,   1,   0,   0,   0,   0

    格式：
        TIMESTAMP  统计时间，每分钟输出一条日志
        TYPE  哪种日志的统计，分别是reqstats, callstats, webstats
        SERVICE_ID  服务号
        MSG_ID   消息号
        SUCCESS  成功数
        FAILURE  失败数
        TIMEOUT  超时数
        CNT_10      耗时在10ms以下的数量
        CNT_25      耗时在25ms以下的数量
        CNT_50      耗时在50ms以下的数量
        CNT_100     耗时在100ms以下的数量
        CNT_250     耗时在250ms以下的数量
        CNT_500     耗时在500ms以下的数量
        CNT_1000    耗时在1s以下的数量
        CNT_3000    耗时在3s以下的数量
        CNT_OTHER   耗时超过3s的数量

# 日志输出控制

    可在logback.xml配置按服务级别来控制是输出该消息的日志到文件中
        <logger name="krpc.reqlog.xxx" level="warn" additivity="false"><appender-ref ref="REQLOG" /></logger>
        xxx指服务号，对不想输出日志的服务可调整level为warn即可关闭该日志
        
    可在logback.xml配置按消息级别来控制是输出该消息的日志到文件中
        <logger name="krpc.reqlog.xxx" level="warn" additivity="false"><appender-ref ref="REQLOG" /></logger>
        xxx指服务号, yyy指消息号，对不想输出日志的服务可调整level为warn即可关闭该日志
    
    