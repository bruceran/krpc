
# version 0.1

    初始版本
    提供基本的rpc功能和http通用网关功能
    
    取各种框架的精华:

			日志： logback java界最好的日志框架，没有之一
			网络层框架： netty4 java界最好的nio框架，没有之一
			protobuff，最好的序列化方案，没有之一, 采用pb的想法来自百度的brpc
			网络包协议：krpc,  参考scalabpe的avenue协议以及百度brpc里的baidu_std协议
			插件加载机制：spi, scalabpe里使用了自定义的一种插件加载方式，spi的想法来自dubbo
			动态代理机制：javasssist, 想法来自dubbo
			和spring的schema集成方式: 想法来自dubbo
			接口契约形式：以proto文件为契约， 想法来自scalabpe的服务描述文件; 感觉也是goole内部的标准契约形式
			接口形式：每个请求一个入参，一个响应； scalabpe实践中最好的接口形式; 感觉也是goole内部的标准接口形式
			错误码还是异常: 错误码； scalabpe实践总结出的最好的编码方式, 使用异常会污染客户端代码; 
			通过服务号和消息号来定位服务：想法来自scalabpe里的avenue协议
			通用的http网关：想法来自scalabpe, 和zuul不谋而合, 超越zuul

    独创之处：
		  
			客户端异步调用以及异步调用组合: 使用java 8的CompletableFuture, 灵活性比其他rpc框架都好
			服务端异步实现：比其他框架的实现都好
			PUSH调用：比其他框架的实现都好
			HTTP通用网关：通用，可扩展，轻量, 不用容器；比其他框架的实现都好

# 和其它框架的简单比较

| feature | krpc | dubbo  |  spring cloud | motan | grpc |  
| ------- | ---- | ------------ |  ------------ | ----- |  ---- |  
| 服务契约 | proto文件 | java接口 | 外部swagger | java接口 | proto文件 | 
| 是否要预生成代码  | 需要 |  不需要 | 不需要 | 不需要 | 需要  |    
| 入参可否多个 | 单一,proto风格 | 可多个 | 可多个 | 可多个 | 单一,proto风格 |  
| 序列化  | pb3  |   hessian2,kryo,pb等 | json | hession2,pb等 | pb3 | 
| 传输层协议  | krpc  | dubbo | http | motan2 | http2 |     
| 传输层  | netty4  | netty4 netty3 mina grizzly | rest template, feign | netty4,netty3 | netty4 |    
| 服务端异步实现  | 支持 | 不支持 | 支持 | 不支持 | 支持 |       
| 客户端异步调用  | 支持,java 8 future  | java 5的future或回调 | 不支持 | 自定义future | 回调 |       
| PUSH调用  | 支持 | 不支持 | 不支持 | 不支持 | 支持 |       
| RPC是否需要web容器  | 不需要 |    不需要 | 需要 | 不需要 | 不需要 |     
| 消息定位  | 服务号+消息号| 服务名+消息名 | url | 服务名+消息名 | 服务名+消息名 |  
| 长连接  | 是 | 是 | 否 | 是 | 是 | 是 |      
| 提供http功能  | 是 |  dubbo无，dubbox有 | 天生 | 是 | 天生 | 否 |     
| http接口定义方式  | routes配置文件 |  注解 | 注解 | 注解 | - |      
| 可否作为通用网关  | 是 |   否 | zuul组件 | 否 |  否 | 
| 错误码风格还是异常风格  | 统一错误码机制 | 异常 | ? | 异常 | 异常 | 
| 注册与发现服务  | consul,etcd,zookeeper,eureka | zookeeper,redis,broadcast | consul,eureka | zookeeper,consul | ? |    
| 监控及APM系统对接  | skywalking,zipkin,cat | 自带监控，主流APM都支持dubbo | 主流APM都支持 | 自带监控 | 主流APM都支持 |       
