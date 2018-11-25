
# version 0.1.21

    增加RpcFutureUtils类, 通过系列wrap函数来包装CompelatbleFuture回调lamdba,
    用于上下文场景恢复，异常日志打印，异常时自动响应

    增加krpc server健康检查
    修改注册与发现过程中创建多余的长连接bug

# version 0.1.19

    监控服务接口增加： 系统信息上报接口，实时报警接口
    增加自检功能， 提供alive,dump,health,refresh接口
    实现krpc框架自身的健康检查
    实现系统信息dump和上报

# version 0.1.15

    增加connection plugin
    将discovery调整为在krpc框架init的时候进行
    增加持久化重试 retryier 功能

# version 0.1.9

    彻底解决启动过程中的业务层和krpc框架的循环依赖问题
    修改rpcclient超时配置错误bug

# version 0.1.0

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

# 和其它框架的基础特性比较

     基础特性的差异使得krpc和其它rpc框架相比具有自己独有的特点。

| feature | krpc | dubbo  |  spring cloud | motan | grpc |  
| ------- | ---- | ------------ |  ------------ | ----- |  ---- |  
| 服务契约 | proto文件 | java接口 | 外部swagger | java接口 | proto文件 | 
| 是否要预生成代码  | 需要 |  不需要 | 不需要 | 不需要 | 需要  |    
| 入参可否多个 | 单一入参对象 | 可多个 | 可多个 | 可多个 | 单一入参对象 |  
| 序列化  | pb3  |   hessian2,kryo,pb等 | json | hession2,pb等 | pb3 | 
| 传输层协议  | krpc  | dubbo | http | motan2 | http2 |     
| 传输层框架  | netty4  | netty4 netty3 mina grizzly | rest template, feign | netty4,netty3 | netty4 |    
| 服务端异步实现  | 支持 | 不支持(回调不算) | 支持 | 不支持 | 支持 |       
| 客户端异步调用  | java 8 CompletableFuture  | java 5的Future和回调 | 支持 | 自定义Future | 自定义Future和回调 |       
| PUSH调用  | 支持 | 不支持 | 不支持 | 不支持 | 支持 |       
| 是否需要web容器  | 不需要 |    不需要 | 需要 | 不需要 | 不需要 |     
| 消息定位  | 服务号+消息号| 服务名+消息名+参数类型 | url | 服务名+消息名+参数类型 | 服务名+消息名 |  
| 错误码还是异常风格  | 错误码,不抛出异常 | 异常 | 异常 | 异常 | 异常 | 
| 长连接  | 是 | 是 | 否 | 是 | 是 | 是 |      
| 提供http功能  | 是 |  dubbo无，dubbox有 | 天生 | 是 | 天生 |    
| http接口定义方式  | routes配置文件 |  注解 | 注解 | 注解 | ? |      
| 可否直接用作通用网关  | 是 |   否 | zuul | 否 |  否 | 
