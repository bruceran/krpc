
# version 0.1

    初始版本
    提供基本的rpc功能和http通用网关功能
    

    取各种框架的精华:

			日志： logback java界最好的日志框架，没有之一
			网络层框架： netty4 java界最好的nio框架，没有之一
			protobuff，最好的序列化方案，没有之一, 采用pb的想法来自百度的brpc
			frame协议：krpc,  参考scalabpe的avenue协议以及百度brpc里的baidu_std协议
			插件加载机制：spi, scalabpe里使用了自定义的一种插件加载方式，spi的想法来自dubbo
			动态代理机制：javasssist, 想法来自dubbo
			和spring的schema集成方式: 想法来自dubbo
			接口契约形式：以proto文件为契约， 想法来自scalabpe的服务描述文件; 感觉也是goole内部的标准契约形式
			接口形式：每个请求一个入参，一个响应； scalabpe实践中最好的接口形式; 感觉也是goole内部的标准接口形式
			错误码还是异常: 错误码； scalabpe实践总结出的最好的编码方式, 使用异常会污染客户端代码; rpc调用出错是正常现象，不是异常;
			通过服务号和消息号来定位服务：想法来自scalabpe里的avenue协议
			通用的http网关：想法来自scalabpe, 和zuul不谋而合, 超越zuul

    独创之处：
		  
			客户端异步调用以及异步调用组合: 使用java 8的CompletableFuture, 灵活性比其他rpc框架都好
			服务端异步实现：比其他框架的实现都好
			PUSH调用：比其他框架的实现都好
			HTTP通用网关：通用，可扩展，轻量, 不用容器；比其他框架的实现都好

# 和其它框架的简单比较

| feature | krpc | dubbo dubbox |  spring cloud | motan | scalabpe | grpc | tars | venus  | 
| ------- | ---- | ------------ |  ------------ | ----- | -------- | ---- | ---------- | ------ | 
| 服务契约 | proto文件 | java接口 | 无 | java接口 | 服务描述文件 | proto文件 | idl文件 | java接口 + 一大堆注解(貌似不可缺少) |
| 是否要预生成代码  | 需要, 生成的接口和普通接口一样 | 不需要 | 不需要 | 不需要 | 不需要 | 需要 生成的java接口不够简洁 简单的同步功能也需要一个异步形式的复杂接口 | 需要, 生成的java接口客户端和服务端不一致, idl编译插件必须用maven插件，使用不方便 | 不需要 |      
| 入参可否多个 | 单一,proto风格 | 可多个 | 可多个 | 可多个 | 单一 | 单一,proto风格 |  单一 | 可多个 |
| 序列化  | pb3  | hessian2 (json,kryo,java,pb等) | json | 同dubbo | tlv | pb3 | tlv | json,bson |
| 传输层协议  | krpc  | dubbo | http | motan2 | avenue | http2, 协议很重 | ? | venus, 协议不够简洁 |      
| 传输层  | netty4  | netty4 netty3 mina grizzly | rest template, feign | netty4,netty3 | netty3 | netty4 | 自研nio框架 | 自研nio框架 |      
| 服务端异步实现  | 支持,简洁  | 不支持 | 不支持 | 不支持 | 全异步 | 不支持 | 不支持 | 回调方式, 接口定义方式有点奇怪，客户端要异步回调貌似要看服务端是否提供异步回调实现 |      
| 客户端异步调用  | 支持,java 8 future,极其强大  | 回调 | 不支持 | 自定义future | 全异步 | 回调 | 回调, 回调接口不友好 | 回调 |      
| PUSH调用  | 支持,简洁 | 支持，但配置复杂 | 不支持 | 不支持 | 支持 | 支持，接口复杂 | 等于不支持 | 不支持 |      
| RPC是否需要web容器  | 不需要 | 不需要 | 需要 | 不需要 | 不需要 | 不需要 | 不需要 | 不需要 |      
| 消息定位  | 服务号+消息号 | 服务名+消息名,对名称改变敏感 | url | 服务名+消息名,对名称改变敏感 | 服务号+消息号 | 服务名+消息名,对名称改变敏感 | ? | 服务名+消息名,对名称改变敏感 |      
| 长连接  | 是 | 是 | 否 | 是 | 是 | 是 | 是 | 是 |      
| 提供http功能  | 是 | dubbo无，dubbox有 | 天生 | 无 | 是 | 天生 | 否 | 是 |      
| http接口定义方式  | routes配置文件 | java接口上加注解 | - | java接口上加注解,实现复杂，需web容器 | routes配置文件 | - | 无 | 需web容器 |      
| 可否作为通用网关  | 是 | 否 | zuul组件 | 否 | 是 | 否 | 否 | 否 |      
| 错误码风格还是异常风格  | 强制统一的错误码机制 | 异常 | 无 | 异常 | 强制统一的错误码机制 | 无 | 无 | 异常 |      
| 框架启动配置方式  | 类dubbo | 简洁 | 无 | 类dubbo | 自有的xml格式文件 | ? | 风格较老 | 自有的xml配置格式 |      
| 注册与发现服务  | consul,etcd,zookeeper | zookeeper,redis,broadcast | consul,eureka | zookeeper,consul | etcd | ? | 自研 | ? |      
| 监控及APM系统对接  | skywalking,zipkin,cat | 自带监控，主流APM都支持 | 主流APM都支持 | 自带监控 | 无 | 主流APM都支持 | 自研 | 自研 |        
| 一句话点评(个人观点)  | 简洁强大现代 | 强大 历史负担太重 难以做出大的变革 | 内网用短连接通讯不够好 | 简洁，但内部实现代码不够好 | 完全不同的开发方式,java界接受度较低 | http2用在内网通讯太重, 另外接口形式不好 | 配套齐全，但设计上过时了且有明显的不足 | 设计上过时了且有明显的不足, 对标是的上一代的web service服务 |      

