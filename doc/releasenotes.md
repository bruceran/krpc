
# version 0.1

    初始版本
    提供基本的rpc功能和http通用网关功能
    
# 和其它框架的简单比较

    取各种框架的精华:

    	日志： logback java界最好的日志框架，没有之一
    	网络层框架： netty4 java界最好的nio框架，没有之一
    	序列化：protobuff，最好的序列化方案，没有之一, 采用pb的想法来自百度的brpc
    	frame协议：krpc,  参考scalabpe的avenue协议以及百度brpc里的baidu_std协议
		  插件加载机制：spi, 想法来自dubbo
		  动态代理机制：javasssist, 想法来自dubbo
		  和spring的schema集成方式: 想法来自dubbo
		  接口契约形式：以proto文件为契约， 想法来自scalabpe的服务描述文件; 也是protobuff对服务接口申明的默认方式
		  接口形式：每个请求一个入参，一个响应； scalabpe实践中最好的接口形式
		  错误码还是异常: 错误码； scalabpe实践中发现最好的编码方式, 使用异常会污染客户端代码; rpc调用出错是正常现象，不是异常;
		  使用服务号和消息号来定位服务：想法来自scalabpe里的avenue协议
		  通用的http网关：想法来自scalabpe, 和zuul不谋而合

    独创之处：
		  
		  客户端异步调用以及异步调用组合: 使用java 8的CompletableFuture, 比其他rpc框架都好
		  服务端异步实现：比其他框架的实现都好
		  PUSH调用：比其他框架的实现都好
		  HTTP通用网关：通用，可扩展，轻量, 不用容器；比其他框架的实现都好
