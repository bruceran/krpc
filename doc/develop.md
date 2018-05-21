
# 整体架构

	* 简洁而不简单的核心架构
	
      客户端业务层代码                                         服务端业务层代码 
	    -------------------------------------------------------------------------
	       启动时生成的动态代理
	       RpcClient            --->  注册与发现服务    <---         RpcServer
	       Cluster Manager
	       Netty4 Transport     --->   krpc codec      <---         Netty4 Transport
	    -------------------------------------------------------------------------
      网络层数据传输               <----  正向或逆向调用  ---->    网络层数据传输
	
	* 强大的功能
		
		  一个进程内通常启动一个app
		  每个app内可以启动多个server,多个client,多个webserver
		  每个app内可启动多个service, service可绑定到不同的server, 或者client(PUSH调用)，或者webserver
		  每个app内可启动多个referer, referer可绑定到不同的client, 或者server(PUSH调用)
		  每个service或referer都可以在method级别做更多配置
		  每个app内可配置一个monitorservice做日志相关配置
		  每个app内可配置多个注册与服务插件，可同时连接多个注册与发现服务
		  
		  框架内的client,server,webserver是重量级对象，因谨慎创建实例；
		  框架内的service/referer是非常轻量的，在框架内部无对应实体，仅仅是一些配置值；
		  启动时生成的动态代理也是非常轻量的，就是一行转发代码到RpcClient
		  对Netty4的封装是只做了最轻量的封装，减少不必要的层次
		  客户端的异步调用返回jdk 1.8的CompleatableFuture<T>, 可以用简单的代码实现各种异步：并行调用，灵活组合多个回调,只投递不关心响应；
		  服务端的异步实现非常简洁
		  逆向调用(PUSH)和正向调用一样简洁
		  强大的HTTP通用网关

	* 强大的扩展机制
	
	    a) 使用预定义的spi接口进行功能扩展
	    b) 框架内部几乎都是以接口方式进行编程，所有实体类的创建都在BootStrap类中，可通过继承BootStrap类做更深度的定制
	    c) 框架本身只对logback,protobuff3,netty4,javassist有强依赖，其它依赖都是可选的, 都是可以替换的
	
# krpc协议

  krpc协议是自定义的TCP长连接协议, 了解底层通讯协议有助于更好地理解krpc框架
	
  每个网络包分为3部分：8字节的固定头部+protobuff形式的扩展包头+protobuff形式的包体(包体可选)
    
  固定头部含义：
    
	       0.......8........16........24.........32
	    1  |-----KR---------|----- headLen--------| //标识和包头长度
	    2  |---------------packetLen--------------| //包体长度
	    
	    前8个字节为固定长度
	    
	    KR 标识 2字节，'KR'这2个特殊字符表示是krpc网络包
	    headLen 2字节 扩展包头长度
	    bodyLen 4字节 扩展包头+包体长度 (不包括8字节的固定头部)

  扩展包头, protobuff形式，长度不定(值越小包越短，默认值不传输)，目前包括以下字段：

      direction int32 1=请求 2=响应
      serviceId int32 服务号
      msgId int32 消息号
      sequence int32 包标识
      traceId string 全链路跟踪标识，此字符串具有以下几个含义：全链路不变的traceId, spanId, parentSpanId
      peers string 网络包经过的所有节点
      retCode int32 错误码，仅用于响应包，某些情况下可以无包体，通过此字段确定错误码
      timeout int32 超时时间，客户端的超时时间可以传给服务端，服务端可以根据此时间快速丢弃队列中已过期未执行的消息
      compress int32 包体是否做了压缩以及压缩方式  0=不压缩 1=zlib 2=snappy
    
      目前服务号1已被框架使用，其中 serviceId=1 msgId=1 为心跳包, 心跳包无sequence
      
  包体, protobuff形式	
      
      框架对请求包无要求
      框架要求业务响应包里必须要有一个retCode来标识错误码
      传输时请求和响应包都可以不传

# 接口定义

	使用google proto文件来定义接口。
	注意必须将2个文件放在 descriptor.proto 和 krpcext.proto 文件放在同一级目录才能编译成功，否则会报错
	
	示例proto文件；
	
    syntax="proto3";
    
    import "krpcext.proto";
    option java_multiple_files=true;
    option java_generic_services=true;
    
    option java_package="com.xxx.userservice.proto";
    option java_outer_classname="UserServiceMetas";

		message LoginReq {
			string userName = 1;
			string password = 2;
		}
		
		message LoginRes {
			int32 retCode = 1;
			string retMsg = 2;
			string userId = 3;
		}
		
		message UpdateProfileReq {
			string userId = 1;
			string mobile = 2;
		}
		
		message UpdateProfileRes {
			int32 retCode = 1;
			string retMsg = 2;
		}
						
		service UserService {
			option (krpc.serviceId) = 100;
			rpc login(LoginReq) returns (LoginRes)  { option (krpc.msgId) = 1; };
			rpc updateProfile(UpdateProfileReq) returns (UpdateProfileRes)  { option (krpc.msgId) = 2; };
		} 
  
  * 以下几行为固定，不可修改:
  
      syntax="proto3";  // 必须使用protobuffer 3版本
      
      import "krpcext.proto"; // 此文件中包含了所有krpc在标准protobuffer上做的扩展定义
      
      option java_multiple_files=true; // 保证生成的java类无嵌套，简化代码
      
      option java_generic_services=true; // 来根据service定义生成java接口, 否则只会生成输入输出类

  * 使用krpc.bat  xxx.proto 文件来生成该文件的服务描述文件
	
	生成的接口：
	
      同步接口形式如下；(客户端和服务端通用)
	
        package com.xxx.userservice.proto;
        
        public interface UserService {
            static final public int serviceId = 100;
        
            com.xxx.userservice.proto.LoginRes login(com.xxx.userservice.proto.LoginReq req);
            static final public int loginMsgId = 1;
        
            com.xxx.userservice.proto.UpdateProfileRes updateProfile(com.xxx.userservice.proto.UpdateProfileReq req);
            static final public int updateProfileMsgId = 2;
        }
        
      异步接口形式如下；(仅用于客户端)
        
        package com.xxx.userservice.proto;
        
        public interface UserServiceAsync {
            static final public int serviceId = 100;
        
            java.util.concurrent.CompletableFuture<com.xxx.userservice.proto.LoginRes> login(com.xxx.userservice.proto.LoginReq req);
            static final public int loginMsgId = 1;
        
            java.util.concurrent.CompletableFuture<com.xxx.userservice.proto.UpdateProfileRes> updateProfile(com.xxx.userservice.proto.UpdateProfileReq req);
            static final public int updateProfileMsgId = 2;
        }
	
	后续可以以下方式之一来使用生成好的文件:
	
    * 将生成好的源码文件拷贝到项目的固定目录下
    * 若不想复制源码只想引用jar包也可拷贝jar包到项目依赖位置（本地目录或maven仓库） (目前暂不支持)
    * 对http通用网关动态调用接口，需要用到生成的 xxx.proto.pb 文件

# 约定

  * 所有业务层服务号从100开始
  
  * 所有消息号从1开始
  
  * 业务层错误码格式建议为： -xxxyyy,  xxx为服务号 yyy为具体错误码，不同服务的错误码不同，如-100001 
  
  * krpc框架内部的错误码为-zzz 只有3位数，和业务层错误码很容易区分
  
  * 框架默认会从 classpath下的 error.properties 文件里根据错误码得到错误提示并放入响应包里，无需在业务层代码中设置响应的retMsg
  
      error.properties 格式如下：
      
    	-100001=参数不正确
  		-100002=用户不存在
	  	  
# 如何启动krpc, 以下展示不用spring框架下如何启动krpc。

  * 参考: src/test/java/krpc/test/call
		
		import krpc.bootstrap.*;

  * 启动服务端：
			
        UserServiceImpl impl = new UserServiceImpl(); // UserServiceImpl是一个实现了UserService接口的类
        
        RpcApp app = new Bootstrap()
          .addServer(5600)  // 去掉这一行绑定默认的5600端口
          .addService(UserService.class,impl) // 增加服务
          .build().initAndStart();

  * 启动客户端：
			
        RpcApp app = new Bootstrap() 
        	.addReferer("us",UserService.class,"127.0.0.1:5600")  // 增加referer, 如果打算使用同步调用需这一行
        	.addReferer("usa",UserServiceAsync.class,"127.0.0.1:5600") // 增加异步referer, 如果打算使用异步调用需这一行
        	.build().initAndStart();
  		
        UserService us = app.getReferer("us"); // 获取同步代理
        UserServiceAsync usa = app.getReferer("usa");  // 获取异步代理
        
        LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();  // pb风格的对象创建
        
        LoginRes res = us.login(req); // 同步调用
        
        CompletableFuture<LoginRes> f1 = usa.login(req);  // 做异步调用
        ... // do other things
        LoginRes res1 = f1.get(); // 获取结果后再处理
  			
        CompletableFuture<LoginRes> f2 = usa.login(req);  // 做异步调用，添加listener
        f2.thenAccept( (res2) -> { log.info("retCode="+res2.getRetCode()+", retMsg="+res2.getRetMsg() ); } );
        ...  // 在CompletableFuture的基础上可以做各种组合
		
  * 在服务中既作为服务端提供服务也作为客户端访问其他服务：
  
        UserServiceImpl impl = new UserServiceImpl(); // 实现UserService接口
        
        RpcApp app = new Bootstrap() 
          .addService(UserService.class,impl) 
          .addReferer("us",Xxx.class,"127.0.0.1:5800") // 此处假设要引用外部Xxx服务
          .build().initAndStart();		
        
        ...
        
        每个rpcapp里可以创建多个service和多个referer

  * 对外提供http接口都需要在classpath下先编辑好routes.xml文件，示例：
		
        <?xml version="1.0" encoding="utf-8"?>    
        <routes>    
          <group hosts="*" prefix="/user"  methods="get,post" serviceId="100">  
          	<url path="/test1" msgId="1"/>  
          	<url path="/test2" msgId="2"/>  
          </group>
        </routes>  
			
  * 服务对外同时提供tcp接口和http接口:
		
        UserServiceImpl impl = new UserServiceImpl();
        
        RpcApp app = new Bootstrap()
          .addWebServer(8888)  // http服务
          .addServer(5600)  // tcp服务, 去掉这一行只对外提供http接口
          .addService(UserService.class,impl)
          .build().initAndStart();
  						
  		  按上述的routes.xml通过以下三种方式访问接口都可以：
          curl -i http://localhost:8888/user/test1?userName=a&password=b
          curl -i -X POST http://localhost:8888/user/test1 -H "Content-Type: application/x-www-form-urlencoded" --data "userName=a&password=b"
          curl -i -X POST http://localhost:8888/user/test1 -H "Content-Type: application/json" --data '{"userName":"a","password":"b"}'
          		  
  * 启动HTTP通用网关(静态方式), 要求网关中classpath中有protoc生成的jar包依赖
		
        RpcApp app = new Bootstrap()
          .addWebServer(8888)  // 相比普通的客户端多出来的一行
          .addReferer("us",UserService.class,"127.0.0.1:5600") 
          .addReferer("usa",UserServiceAsync.class,"127.0.0.1:5600") 
          .build().initAndStart();

  * 启动HTTP通用网关(动态方式), 网关中不用jar包，只用生成的userservice.proto.pb文件
  		
        RpcApp app = new Bootstrap()
          .addWebServer(8888) 
          .addReferer("us",100,"127.0.0.1:5600") // 第二个参数不用接口名而是改用服务号			
          .build().initAndStart();

# 和spring框架集成(java config方式)
		
  * 服务端参考；src/test/java/krpc/test/javaconfig/server
		
        服务端： 实现userservice接口：
        
        @Component("userService")
        class UserServiceImpl implements UserService {
          ...		
        }
      
        服务端： 在java config文件里启动krpc：
      
        @Configuration
        @ComponentScan(basePackages = "krpc.test.javaconfig.server" }) // 扫描此目录下的所有bean去获取UserService实例
        public class MyServerJavaConfig   {
        
          @Bean(destroyMethod = "stopAndClose")
          public RpcApp rpcApp(UserService userService) { // 自动注入该服务
            RpcApp app = new Bootstrap() 
            		.addService(UserService.class,userService) 
            		.build().initAndStart();
            return app;
          }
          
          ... // 其它bean
        }

  * 客户端参考: src/test/java/krpc/test/javaconfig/client
      
        客户端： 在java config文件里启动krpc：
      
            @Bean(destroyMethod = "stopAndClose")
            public RpcApp rpcApp() {
              RpcApp app = new Bootstrap() 
                .addReferer("us",UserService.class,"127.0.0.1:5600") 
                .addReferer("usa",UserServiceAsync.class,"127.0.0.1:5600")         			
                .build().initAndStart();
              return app;
            }
            
            @Bean
            public UserService userService(RpcApp app) {
              UserService us = app.getReferer("us");
              return us;
            }
            
            @Bean
            public UserServiceAsync userServiceAsync(RpcApp app) {
              UserServiceAsync usa = app.getReferer("usa");
              return usa;
            }            
    		
# 和spring框架集成(schema方式)

  服务端参考；src/test/java/krpc/test/schema
  
    spring-schema-server.xml
    spring-schema-client.xml
  
  这种配置方式形式上来自dubbo，但具体配置值不同，参考配置参数详解

# 和spring框架集成(spring boot方式)
  
  暂不支持
		
# 配置参数详解				  

	可打开 src/main/resources/krpc.xsd 了解框架支持哪些配置参数, 每个参数的具体含义如下：
	
## application

    name 应用名，用在上报给注册与发现服务时使用, 默认为default_app
    mockFile 开发阶段可通过此文件来对service做mock, 暂未实现
    errorMsgConverter 错误码错误消息转换文件，默认为classpath下的error.properties
    flowControl 流量控制策略，默认不启用, 可配置为 memory 或 redis (暂未实现)
    
## registry

    id 名称, 必须填写
    type 注册与发现服务的类型, 会支持几种常见的: zookeeper, etcd, consul, 暂未实现
    address 注册与发现服务连接地址

## client
 
    id 名称 不填则会自动生成
    pingSeconds  心跳间隔时间，秒，默认为60
    maxPackageSize  最大包长，字节， 默认为 1000000
    connectTimeout 连接超时 毫秒， 默认为15000
    reconnectSeconds  重连间隔，秒，默认为1
    ioThreads  netty4内部io读写线程，默认为0，由系统自动分配
    connections 每个地址建立的连接数， 默认为1, 如果发现netty4单连接已经出现io瓶颈可增打连接数
    notifyThreads 当使用异步调用时，异步回调的线程池线程数量，默认为0，由系统自动分配
    notifyMaxThreads 同上，可配置一个大于notifyThreads的值，默认为0，也就是notifyMaxThreads=notifyThreads
    notifyQueueSize 同上，线程池中固定队列大小，默认为10000
    threads 当使用PUSH调用时, client可以作为server, 此时收到的请求在此线程池中运行, 默认为0由系统自动分配，可配置为-1不单独建立线程池，直接使用netty io线程；或>0的值
    maxThreads 同上，可配置一个大于threads的值，默认为0，也就是maxThreads=threads
    queueSize 同上，线程池中固定队列大小，默认为10000
	
## server	

    id 名称 不填则会自动生成
    port  绑定的端口，默认为 5600
    host  绑定的IP, 默认为*， 绑定所有IP
    idleSeconds 允许的最大读写超时时间，秒，默认为180
    maxPackageSize 最大包长，字节， 默认为 1000000
    maxConns 服务端允许的同时的客户端连接数，默认为500000
    ioThreads netty4内部io读写线程，默认为0，由系统自动分配
    notifyThreads  当使用PUSH调用时, server可以作为client, 这时若采用异步方式调用客户端，异步回调的线程池线程数量，默认为0，由系统自动分配
    notifyMaxThreads 同上，可配置一个大于notifyThreads的值，默认为0，也就是notifyMaxThreads=notifyThreads
    notifyQueueSize 同上，线程池中固定队列大小，默认为10000
    threads  服务端收到的请求在此线程池中运行, 默认为0由系统自动分配，可配置为-1不单独建立线程池，直接使用netty io线程；或>0的值
    maxThreads  同上，可配置一个大于threads的值，默认为0，也就是maxThreads=threads
    queueSize 同上，线程池中固定队列大小，默认为10000

## webserver	

    id 名称 不填则会自动生成
    port  绑定的端口，默认为 8600
    host  绑定的IP, 默认为*， 绑定所有IP
    idleSeconds  允许的最大读写超时时间，秒，默认为60
    maxContentLength 最大包长，字节， 默认为 1000000 (文件上传会有单独的配置参数控制大小)
    maxConns 服务端允许的同时的客户端连接数，默认为500000
    ioThreads  netty4内部io读写线程，默认为0，由系统自动分配
    notifyThreads  当使用PUSH调用时, server可以作为client, 这时若采用异步方式调用客户端，异步回调的线程池线程数量，默认为0，由系统自动分配
    notifyMaxThreads 同上，可配置一个大于notifyThreads的值，默认为0，也就是notifyMaxThreads=notifyThreads
    notifyQueueSize 同上，线程池中固定队列大小，默认为10000
    threads  服务端收到的请求在此线程池中运行, 默认为0由系统自动分配，可配置为-1不单独建立线程池，直接使用netty io线程；或>0的值
    maxThreads  同上，可配置一个大于threads的值，默认为0，也就是maxThreads=threads
    queueSize 同上，线程池中固定队列大小，默认为10000
         
    sessionService  会话服务插件, 支持 memory,redis(暂未实现), 默认为memory
    jsonConverter json序列化使用的json框架，默认为 jackson
    routesFile 路由配置文件， 默认为 routes.xml，会自动搜索classpath下的routes.xml配置文件
    sessionIdCookieName  SESSIONID 采用的 COOKIE 名，默认为 JSESSIONID
    sessionIdCookiePath  输出 SESSIONID cookie 的路径，默认为空，表示当前目录
    
    protoDir proto文件所在目录，默认为 proto, 会自动搜索classpath下的proto/子目录下的所有xxx.proto.pb文件

## service

    id 名称 不填则会自动生成
    interfaceName 接口名
    impl 实现类的bean name ref
    transport 引用的server或webserver或client的id, 如果reverse=false, 则对应server或webserver的id; 如果reverse=true, 则对应client的id;
    reverse 正向调用还是逆向调用, 值为 true 或 false, 默认为 false
    registryNames 注册与发现服务名, 可填多个，用逗号隔开, 引用的是 registry的id
    group  注册与发现服务里的分组
    threads 服务级别的线程池配置参数, 含义同server, 默认为-1，不启用单独的线程池
    maxThreads 服务级别的线程池配置参数, 含义同server
    queueSize 服务级别的线程池配置参数, 含义同server
    flowControl 流量控制参数，前提要app级别开启了流量控制，格式为：seconds1=allowed1;seconds2=allowed2;... 示例： 1=5;3=20 表示1秒内允许5次调用，3秒内允许20次调用
    
    每个service可配置0个或多个method在消息级别做配置

## referer

    id 名称 不填则会自动生成
    interfaceName 接口名
    serviceId 服务号 (http动态网关无接口类，根据服务号来配置)，不可和interfaceName同时使用
    transport  引用的client或server的id, 如果reverse=false, 则对应client的id; 如果reverse=true, 则对应server的id;
    reverse 正向调用还是逆向调用, 值为 true 或 false, 默认为 false
    direct 指定此参数可直连服务，无需通过注册与发现服务
    registryName  注册与发现服务名, 只能填一个
    group  册与发现服务里的分组
    timeout 超时时间, 毫秒，默认为3000
    retryLevel 重试级别, 默认为 no_retry
    retryCount 重试次数，默认为0
    loadBalance 负载均衡策略，可配置为 rr,random,responsetime, 默认为 rr
    zip 压缩方式 0=不压缩 1=zlib 2=snappy
    minSizeToZip 启用压缩所需的最小字节数, 默认为10000
 
    每个referer可配置0个或多个method在消息级别做配置
 
## method	    

    pattern 消息匹配模式
      若第一个字符是数值，则表示是以消息ID作为匹配模式，格式示例：1-3,8,100-200,... 以逗号为分割符，以-来指定一段连续消息ID
      若第一个字符不是数值，则表示是以正则表达式来匹配消息名
    
    以下3个参数只用于referer
    timeout 消息级别的超时时间，毫秒，默认为3000
    retryLevel 消息级别的重试级别, 默认为 no_retry
    retryCount 消息级别的试次数，默认为0
    
    以下4个参数只用于service
    threads 消息级别的线程池配置参数, 含义同server, 默认为-1，不启用单独的线程池
    maxThreads 消息级别的线程池配置参数, 含义同server
    queueSize 消息级别的线程池配置参数, 含义同server
    flowControl 消息控制参数，前提要app级别开启了流量控制，格式为：seconds1=allowed1;seconds2=allowed2;... 示例： 1=5;3=20 表示1秒内允许5次调用，3秒内允许20次调用
    
## monitor
 
    maskFields 日志里要屏蔽的字段，屏蔽后输出***代替原来的值
    maxRepeatedSizeToLog 对repeated参数, 输出前n项，否则日志会太大，默认为1
    logFormatter 日志格式，可选 simple, jackson， 默认为simple
    logThreads 异步输出日志的线程数，默认为1
    logQueueSize 异步输出日志的固定队列大小，默认为10000
    serverAddr 监控服务地址
    printDefault 是否输出protobuff消息里的默认值, 默认为false
    
# routes.xml配置				  

	启动webserver需要一个配套的rouets.xml, 否则webserver不知道如何路由
	routes.xml 必须放在classpath目录下
	
	示例：
	
      <?xml version="1.0" encoding="utf-8"?>    
      <routes>    
      
          <import file="routes-b.xml"/>  
          
          <plugin name="dummy" params="a=1;b=2"/>
          
          <dir hosts="*" path="/test1" baseDir="c:\ws"/>  
          
          <url hosts="*" path="/user/test1" methods="get,post" serviceId="100" msgId="1" plugins="dummy" sessionMode="0"/>  
          <url hosts="*" path="/user/test2" methods="get,post" serviceId="100" msgId="2"/>  
          
          <group   prefix="/abc"  methods="get,post" serviceId="100">  
            <url path="/test3" msgId="3"/>  
            <url path="/test4" msgId="4"/>  
          </group>
      
      </routes>
      
  * 可通过import导入其它routes文件，这样可以按服务分别存放路由
  
  * 可通过dir定义静态资源目录，上传目录等
  
  * 每个url标识一个路由映射, 可直接放在routes下，也可放在group下, 通常总是一类消息会共用相同的配置，建议都放在group下 

  * 每个url支持以下属性：
  
        hosts 允许的域名，*表示不限制，默认为*; 通用网关支持按不同的域名分开配置
        path 访问路径,  path中支持变量， 如 /abc/{region}/{userId}, 以支持纯rest风格的开发
        methods 访问方法，支持get,post,put,delete, 默认为get,post; 如果body是json格式，默认也会直接做解析，无需额外配置
        serviceId path对应的服务号
        msgId path对应的消息号
        plugins 用来配置插件名，允许多个，用逗号隔开
        sessionMode 会话模式 0=不需要会话 1=只需要会话ID 2=有会话则把会话信息传给后端，但不强制登录 2=必须要登录, 默认为0

        每个url里的其它属性也会保存下来，如果自定义插件需要一些扩展属性，也可以从context中获取到这些自定义的属性
        
  * group用来配置一组url公共的属性，简化url配置
  
        group节点不支持配置 path 和 msgId
        group节点允许配置的节点:
        
        hosts 允许的域名，*表示不限制，默认为*
        prefix 若配置了此值，则所有路径为 prefix + path, 默认为空
        methods 访问方法，支持get,post,head,put,delete, 默认为get,post; 如果post body是json格式，默认也会直接做解析，无需额外配置
        serviceId path对应的服务号
        plugins 用来配置插件名，允许多个，用逗号隔开
        sessionMode 会话模式 0=不需要会话 1=只需要会话ID 2=有会话则把会话信息传给后端，但不强制登录 2=必须要登录, 默认为0

  * 绝大多数插件并不需要配置参数，不需要参数的插件直接在plugins里引用就可以；如果插件需要配置参数，则需使用plugin节点来进行参数配置
  
        name 插件名
        params 插件参数，由插件自行解析的字符串，系统自带插件采用的风格为 a=1;b=2 这种形式，使用分号和等于号做分隔符
        
# HTTP通用网关参数映射
    
      webserver框架会自动将http里的http元信息，header,cookie,session,入参等映射到protobuff请求消息；
      webserver框架也会自动将protobuff响应消息映射到http里的http元信息,header,cookie,操作session, 输出内容等；
      通过以上的机制，webserver可以承担一个通用网关的功能，业务开发无需在http层再做开发，只需开发后台服务;
      webserver提供强大的扩展机制，业务可根据自己的需要开发必要的插件来实现一些特殊功能：比如特定的签名校验方法，特殊的内容输出等
      
      请求映射规则：
      
        按需映射，不关心的参数就不要在protobuffer消息里定义, 关心的参数按名称定义即可
        
        常规参数映射，按参数名映射到protobuffer消息里的参数名
        session 里的信息 -> 按参数名映射到protobuffer消息里的参数名 (名称冲突则总是session里的优先, 客户端无法覆盖session参数)
        
        特殊参数映射，如果有需要，后端服务可以获取到http调用的所有细节, 一般建议不要去获取这些特殊信息
        
            session id -> sessionId
            http method -> httpMethod 值为 get,post,put,delete
            http schema -> httpSchema 值为 http,https
            http path -> httpPath 值为 http,https, 不含?后及以后的值
            http host -> httpHost 值为 header里的host值
            http query string -> httpQueryString 值为uri后?号以后的值
            http content-type -> httpContentType header里的content-type值, 去除;号以后的附加参数
            http content -> httpContent 值为http的content
            http header -> headerXxx 映射到protobuffer消息里以header开头的参数名, 需做名称转换，如 User-Agent，在pb里必须定义为headerUserAgent
            http cookie -> cookieXxx 映射到protobuffer消息里以cookie开头的参数名, xxx和cookie名严格保持一致，区分大小写
            
      响应映射规则：
        
        protobuff的消息里如带一些特殊参数，则会先做处理再从响应里删除再转换为json输出
        
        httpCode 单值 -> 会转换为实际的http code, 不设置则默认为200
        httpContentType 单值 -> 会转换为实际的http header里的content-type
        headerXxx 单值 -> 会转换为http输出的 header     
        cookieXxx 单值 -> 会转换为http输出的 cookie, cookie可带参数，格式为：值^key=value;key=value;...  key支持 domain,path,maxAge,httpOnly,secure,wrap    
        session 不能是单值，必须是消息(Map) -> 
          如果成功完成登录，后端服务返回的session中应带 loginFlag=1, 以便框架在后续做登录检验；
          如 session 消息里带 loginFlag = 0 则会删除会话；否则将返回的session信息保存到会话上; 
          后续收到请求会自动将会话里的信息做登录验证，并把已登录信息转发给后端服务
          常规情况下后端服务不用去存储会话信息，也不用关心sessionId; 如果有特殊需求，后端也可以根据sessionId做自己的存储策略
        
        以上处理完毕后将剩余消息转换成json并输出, 如需控制输出格式或内容，可通过插件进行定制
    
