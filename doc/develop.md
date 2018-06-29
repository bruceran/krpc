
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
	
      概念：
        rpc app              每个使用krpc的应用都认为是一个app, 每个app具有一个名称，用于服务注册和发现以及调用链跟踪
        rpc server           提供krpc协议服务的server，需要绑定物理端口，接收客户端连接
        rpc webserver        提供http协议服务的webserver，需要绑定物理端口，接收客户端连接
        rpc client           访问krpc协议的客户端, 和rpc server之间建立长连接
        rpc service          对应一个proto里的service或者一个java接口的实现
        rpc referer          对应一个proto里的service或者一个java接口的动态代理
        rpc registry         注册与服务发现组件
        rpc monitor          监控服务
        
        一个进程内通常启动一个app
        每个app内可以启动多个server,多个client,多个webserver
        每个app内可启动多个service, service可绑定到server/webserver(常规) 或者client(PUSH调用)
        每个app内可启动多个referer, referer可绑定到client(常规)或者server(PUSH调用)
        每个service或referer都可以在method级别做更多配置
        webserver,server,client,service,referer之间可通过灵活简洁的组合提供不同的服务：
        
             常规组合 client + referer -> server + service
             PUSH推送 client + service -> server + referer
             同时启动TCP端口和HTTP端口  server + webserver + service
             HTTP网关(需java类)  webserver + client + referer + protoc生成的java类
             HTTP网关(无需java类)  webserver + client + referer + protoc生成的.proto.pb文件
             只对外提供HTTP服务不访问后台服务  webserver + service  如上传或测试
             纯静态页面HTTP网关  webserver
             
        每个app内可配置一个monitorservice做日志相关配置
        简洁的TRACE接口, 仅需调整一个配置就可对接不同的全链路跟踪系统(APM系统)
        每个app内可配置多个注册与服务插件，每个service可同时连接多个注册与发现服务
        
        框架内的client,server,webserver是重量级对象，因谨慎创建实例；
        框架内的service/referer是非常轻量的，在框架内部无对应实体，仅仅是一些配置值；
        启动时生成的动态代理是非常轻量的，仅仅是一行转发代码到RpcClient
        对Netty4的封装是只做了最轻量的封装，减少不必要的层次
        客户端的异步调用返回jdk 8的CompleatableFuture<T>, 可以用简单的代码实现各种异步：并行调用，灵活组合多个回调；
        服务端的异步实现非常简洁
        逆向调用(PUSH)和正向调用一样简洁
        强大的HTTP通用网关

	* 强大的扩展机制

	    a) 使用预定义的spi接口进行功能扩展
	    b) 使用Spring bean扩展spi接口
	    b) 继承BootStrap类做更深度的定制
	    c) 框架本身只对logback,protobuff3,netty4,javassist,jackson有强依赖，其它依赖都是可选的, 都是可以替换的
	
# krpc网络包协议

  krpc网络包协议是自定义的TCP长连接协议, 了解底层通讯协议有助于更好地理解krpc框架
	
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
      traceId string 全链路跟踪标识, 不同的全链路跟踪系统格式不一样
      rpcId string 全链路跟踪RPCID, 不同的全链路跟踪系统格式不一样
      sampled int32 是否采样 0=默认(是) 1=强制,忽略存储级配置 2=否
      peers string 网络包经过的所有节点的ip:port
      apps string 网络包经过的所有节点的app name
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
      
      import "krpcext.proto"; // 此文件中包含了所有krpc在标准protobuffer上做的扩展定义, 增加了krpc.serviceId 和krpc.msgId两个扩展
      
      option java_multiple_files=true; // 保证生成的java类无嵌套，简化代码
      
      option java_generic_services=true; // 来根据service定义生成java接口, 否则只会生成输入输出类

  * 不建议使用pb3里的新特性：Any和OneOf
  
  * 使用krpc.bat  xxx.proto 文件来生成该文件的服务描述文件
	
	生成的接口：
	
      同步接口形式如下；(客户端和服务端通用, 服务端仅需实现这接口)
	
        package com.xxx.userservice.proto;
        
        public interface UserService {
            static final public int serviceId = 100;
        
            LoginRes login(LoginReq req);
            static final public int loginMsgId = 1;
        
            UpdateProfileRes updateProfile(UpdateProfileReq req);
            static final public int updateProfileMsgId = 2;
        }
        
      异步接口形式如下；(仅用于客户端调用调用)
        
        package com.xxx.userservice.proto;
        
        public interface UserServiceAsync {
            static final public int serviceId = 100;
        
            CompletableFuture<LoginRes> login(LoginReq req);
            static final public int loginMsgId = 1;
        
            CompletableFuture<UpdateProfileRes> updateProfile(UpdateProfileReq req);
            static final public int updateProfileMsgId = 2;
        }
	
	后续可以以下方式之一来使用生成好的文件:
	
    * 将生成好的源码文件拷贝到项目的固定目录下
    * 若不想复制源码只想引用jar包也可拷贝jar包到项目依赖位置（本地目录或maven仓库） (目前暂不支持)
    * 对http通用网关动态调用接口，需要用到生成的 xxx.proto.pb 文件

# 服务号和错误码约定

  * 所有业务层服务号使用3位数或4位数，建议使用4位数以便以后更容易空战，从1000开始
  
  * 所有消息号从1开始
  
  * 业务层错误码格式建议为： -xxxxyyy,  xxxx为服务号 yyy为具体错误码，不同服务的错误码不同，如-1000001 
  
  * krpc框架内部的错误码为-zzz 只有3位数，和业务层错误码很容易区分
  
  * 框架默认会从 classpath下的 error.properties 文件里根据错误码得到错误提示并放入响应包里，无需在业务层代码中设置响应的retMsg
  
      error.properties 格式如下：
      
    	-1000001=参数不正确
  		-1000002=用户不存在

  * 框架内部使用的错误码, 具体含义参考 krpc.rpc.common.RetCodes.java 类 
        
        业务层无需判断具体错误码值，只需判断是否为0来确定是否成功
	 	  	  
# 如何启动krpc

  * 以下展示不用spring框架下如何启动krpc
  * 参考: src/test/java/krpc/test/rpc
		
		import krpc.rpc.bootstrap.*;

  * 启动服务端：
			
        UserServiceImpl impl = new UserServiceImpl(); // UserServiceImpl是一个实现了UserService接口的类
        
        RpcApp app = new Bootstrap()
          .addServer(5600)  // 去掉这一行绑定默认的5600端口
          .addService(UserService.class,impl) // 增加服务
          .build().initAndStart();

  * 启动客户端：
			
        RpcApp app = new Bootstrap() 
        	.addReferer("us",UserService.class,"127.0.0.1:5600")  // 如果打算使用同步调用需这一行
        	.build().initAndStart();
  		
        UserService us = app.getReferer("us"); // 获取同步代理
        UserServiceAsync usa = app.getReferer("usAsync");  // 获取异步代理, 命名约定：同步代理的名称后+Async获取异步代理
        
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

  * 对外提供http接口都需要在classpath下先编辑好webroutes.xml文件，示例：
		
        <?xml version="1.0" encoding="utf-8"?>    
        <routes>    
          	<url path="/user/test1" serviceId="100" msgId="1"/>  
          	<url path="/user/test2" serviceId="100" msgId="2"/>  
        </routes>  
			
  * 服务对外同时提供tcp接口和http接口:
		
        UserServiceImpl impl = new UserServiceImpl();
        
        RpcApp app = new Bootstrap()
          .addWebServer(8888)  // http服务
          .addServer(5600)  // tcp服务, 去掉这一行只对外提供http接口
          .addService(UserService.class,impl)
          .build().initAndStart();
  						
  		  按上述的webroutes.xml通过以下三种方式访问接口都可以：
          curl -i http://localhost:8888/user/test1?userName=a&password=b
          curl -i -X POST http://localhost:8888/user/test1 
               -H "Content-Type: application/x-www-form-urlencoded" --data "userName=a&password=b"
          curl -i -X POST http://localhost:8888/user/test1 
               -H "Content-Type: application/json" --data '{"userName":"a","password":"b"}'
          		  
  * 启动HTTP通用网关(静态方式), 要求集成protoc生成的源码或jar包
		
        RpcApp app = new Bootstrap()
          .addWebServer(8888)  // 相比普通的客户端多出来的一行
          .addReferer("us",UserService.class,"127.0.0.1:5600") 
          .build().initAndStart();

  * 启动HTTP通用网关(动态方式), 网关中不用集成protoc生成的源码或jar包，只用生成的userservice.proto.pb文件
  		
        RpcApp app = new Bootstrap()
          .addWebServer(8888) 
          .addReferer("us",100,"127.0.0.1:5600") // 第二个参数不用接口名而是改用服务号			
          .build().initAndStart();

# 和spring框架集成(java config方式)
		
  * 服务端参考：src/test/java/krpc/test/rpc/javaconfig/server
		
        服务端： 实现userservice接口：
        
        @Component("userService")
        class UserServiceImpl implements UserService {
          ...		
        }
      
        服务端： 在java config文件里启动krpc：
      
        @Configuration
        @ComponentScan(basePackages = "krpc.test.rpc.javaconfig.server" })
        public class MyServerJavaConfig   {
        
          @Bean(initMethod = "init", destroyMethod = "close")
          public RpcApp rpcApp(UserService userService) { // 自动注入该服务
            RpcApp app = new Bootstrap() 
            		.addService(UserService.class,userService) 
            		.build();
            return app;
          }
          
          ... // 其它bean
        }

  * 客户端参考: src/test/java/krpc/test/rpc/javaconfig/client
      
        客户端： 在java config文件里启动krpc：
      
            @Bean(initMethod = "init", destroyMethod = "close")
            public RpcApp rpcApp() {
              RpcApp app = new Bootstrap() 
                .addReferer("us",UserService.class,"127.0.0.1:5600")     			
                .build();
              return app;
            }
            
            @Bean
            public UserService userService(RpcApp app) {
              UserService us = app.getReferer("us");
              return us;
            }
            
            @Bean
            public UserServiceAsync userServiceAsync(RpcApp app) {
              UserServiceAsync usa = app.getReferer("usAsync");
              return usa;
            }            
    		
# 和spring框架集成(schema方式)

  参考：src/test/java/krpc/test/rpc/schema
  
    spring-schema-server.xml
    spring-schema-client.xml

    referer 的 id 可不配置，若不配置则自动根据接口名的SimpleName自动生成
    无需对客户端异步代理做配置, 只要配置了同步代理，框架会自动生成一个名称为
    同步代理BeanName+Async的异步代理Bean，程序里直接引用该异步代理就可以

# 和spring框架集成(spring boot方式)
  
  参考：doc/samples/boot1 (spring boot 1.x下)  和  doc/samples/boot2 (spring boot 2.x下)
  
    仅需要使用 配置文件 application.yaml 或等价的 application.properties 就可完成krpc的初始化, 无需写代码或xml文件。
    
    需要将 krpc.enabled 设置为 true 才会开启krpc的自动配置功能
    
    以krpc前缀的配置项的含义同 schema 方式的配置项, 对应关系如下：
    
		krpc.application 对应 krpc:application
		krpc.monitor 对应 krpc:monitor
		krpc.registry 和 krpc.registries 对应 krpc:registry, 当只有一个时使用 registry, 多个时使用registries
		krpc.server 和 krpc.servers 对应 krpc:server, 当只有一个时使用使用 server, 多个时使用servers
		krpc.webserver 和 krpc.webservers 对应 krpc:webserver, 当只有一个时使用 webserver, 多个时使用webservers
		krpc.client 和 krpc.clients 对应 krpc:client, 当只有一个时使用 client, 多个时使用clients
		krpc.service 和 krpc.services 对应 krpc:service, 当只有一个时使用 service, 多个时使用services
		krpc.referer 和 krpc.referers 对应 krpc:referer, 当只有一个时使用 referer, 多个时使用referers

    referer 的 id 可不配置，若不配置则自动根据接口名的SimpleName自动生成
    无需对客户端异步代理做配置, 只要配置了同步代理，框架会自动生成一个名称为
    同步代理BeanName+Async的异步代理Bean，程序里直接引用该异步代理就可以

# 配置参数详解				  

	可打开 src/main/resources/krpc.xsd 了解框架支持哪些配置参数, 每个参数的具体含义如下：
	
## application

    name 应用名，用在上报给注册与发现服务时使用, 默认为default_app
    dataDir 数据文件保存目录，默认为当前目录
    delayStart 延迟调用start(),  
        0=容器启动完成后立即调用start(), 
        n>0 =容器启动完成后再延迟n秒调用start(), 
        n<0 用户代码手工调用start()
    
    errorMsgConverter 错误码错误消息转换文件，默认为file
        file 插件参数：location 文件位置，默认为classpath下的error.properties
    dynamicRoutePlugin 动态路由插件，可配置为 consul,etcd,zookeeper,jedis 插件, 如果启动了同名的注册与发现插件，
        则自动使用同名的注册与发现插件
        动态路由插件可以和注册与发现插件混搭，比如可以使用 eureka 注册插件搭配 zookeeper的动态路由插件  
        consul/etcd/zookeeper/jedis 插件参数：addrs 服务器地址, intervalSeconds 刷新间隔时间
        jedis插件参数：clusterMode 是否集群模式
        如果未设置此值，则不开启动态路由功能
    fallbackPlugin  降级策略插件, 可配置为 default(默认), 如果未配置， 则不开启降级策略	
        default 插件参数：file 文件位置，默认为classpath下的 fallback.yaml						 
    traceAdapter 调用链跟踪系统标识，目前支持default(默认), zipkin, skywalking(暂未实现), cat(暂未实现)

## registry

    id 名称, 必须填写
    type 注册与发现服务的类型, 会支持几种常见的: consul, etcd, zookeeper, jedis
    addrs 注册与发现服务连接地址
    enableRegist 是否进行注册，默认 true
    enableDiscover 是否进行发现，默认 true
    params 注册与发现服务附加参数，格式为 k=v;k=v;..., 目前支持的key如下：
        ttlSeconds 多长时间超时，默认 90秒, 适用于 consul, etcd, jedis
        intervalSeconds 多长时间和注册与发现服务做心跳，默认15秒, 适用于 consul, etcd, zookeeper, jedis
        clusterMode redis是否集群模式, 默认为false, 仅用于 jedis插件
        
## server	

    id 名称 不填则会自动生成
    port  绑定的端口，默认为 5600
    host  绑定的IP, 默认为*， 绑定所有IP
    backlog 监听队列backlog数量 默认128
    idleSeconds 允许的最大读写超时时间，秒，默认为180
    maxPackageSize 最大包长，字节， 默认为 1000000
    maxConns 服务端允许的同时的客户端连接数，默认为500000
    ioThreads netty4内部io读写线程，默认为0，由系统自动分配
    notifyThreads  当使用PUSH调用时, server可以作为client, 这时若采用异步方式调用客户端，
                   异步回调的线程池线程数量，默认为0，由系统自动分配
    notifyMaxThreads 同上，可配置一个大于notifyThreads的值，默认为0，也就是notifyMaxThreads=notifyThreads
    notifyQueueSize 同上，线程池中固定队列大小，默认为10000
    threads  服务端收到的请求在此线程池中运行, 默认为0由系统自动分配，
             可配置为-1不单独建立线程池，直接使用netty io线程；或>0的值
    maxThreads  同上，可配置一个大于threads的值，默认为0，也就是maxThreads=threads
    queueSize 同上，线程池中固定队列大小，默认为10000
    plugins 用来配置插件名，允许多个，用逗号隔开
    pluginParams  插件参数配置, 类型为List<String>, 所有需要配置参数的插件通过此参数进行配置，不需要配置参数的插件不用配置
	      每行配置一个插件，格式为 插件名:插件参数，  插件参数的格式自定义，标准风格是 a=b;c=d;...
		  memoryflowcontrol插件参数：
		        memoryflowcontrol 只有流控阈值配置参数，格式有两种：
		        service:{serviceId}:{seconds}={limit}
		        msg:{serviceId}:{msgId#msgId#...}:{seconds}={limit}
			        serviceId指服务号
			        msgId指消息号，可一次指定多个多个，用#分隔
			        seconds 滑动窗口秒数
			        limit 允许的调用次
				示例：
				   service:100:5=50  服务号100的服务5秒内只允许50个请求
				   msg:100:1#2#3:10=100  服务号100的服务消息号为1,2,3的消息在10秒内只允许100个请求
	      jedisflowcontrol插件参数：
	     		clusterMode 是否是redis cluster, 默认为false
	     		addrs 连接地址
	     		keyPrefix key前缀，默认为 FC_       
	     		threads  后台更新线程数, 默认为1                 
	     		maxThreads 后台更新最大线程数, 默认为1
	     		queueSize 后台更新队列数, 默认为10000
	     		syncUpdate 是否同步累加，默认为false, 如设置为true则所有次数累加同步进行，误差会比false模式小，
	     		                  但会增加请求延迟
	     		流控阈值配置参数同 memoryflowcontrol 
	     memoryconcurrentflowcontrol插件参数：
		       格式有两种：
		        service:{serviceId}={limit}
		        msg:{serviceId}:{msgId#msgId#...}={limit}
			        serviceId指服务号
			        msgId指消息号，可一次指定多个多个，用#分隔
			        limit 可同时执行的请求数
				示例：
				   service:100=500  服务号100的服务允许同时执行500个请求
				   msg:100:1#2#3=100  服务号100的服务消息号为1,2,3的消息每个允许同时执行100个请求                   
	     		注意：服务端异步实现的服务需注意必须要有返回(不可丢弃请求), 如果不返回可能导致并发数用完后停止服务
                         		
## webserver	

    id 名称 不填则会自动生成
    port  绑定的端口，默认为 8600
    host  绑定的IP, 默认为*， 绑定所有IP
    backlog 监听队列backlog数量 默认128
    idleSeconds  允许的最大读写超时时间，秒，默认为60
    maxConns 服务端允许的同时的客户端连接数，默认为500000
    ioThreads  netty4内部io读写线程，默认为0，由系统自动分配
    notifyThreads  当使用PUSH调用时, server可以作为client, 这时若采用异步方式调用客户端，
                   异步回调的线程池线程数量，默认为0，由系统自动分配
    notifyMaxThreads 同上，可配置一个大于notifyThreads的值，默认为0，也就是notifyMaxThreads=notifyThreads
    notifyQueueSize 同上，线程池中固定队列大小，默认为10000
    threads  服务端收到的请求在此线程池中运行, 默认为0由系统自动分配，
             可配置为-1不单独建立线程池，直接使用netty io线程；或>0的值
    maxThreads  同上，可配置一个大于threads的值，默认为0，也就是maxThreads=threads
    queueSize 同上，线程池中固定队列大小，默认为10000
         
    maxContentLength 最大包长，字节， 默认为 1000000 (1M)
    maxUploadLength 上传时允许最大长度，字节， 默认为 5000000 (5M)
	maxInitialLineLength HTTP初始行最大长度，字节， 默认为 4096
	maxHeaderSize  HTTP header最大长度，字节， 默认为 8192
	maxChunkSize  HTTP chunk最大长度，字节， 默认为 8192

    protoDir proto文件所在目录，默认为 proto, 会自动搜索classpath下的proto/子目录下的所有xxx.proto.pb文件
    routesFile 路由配置文件， 默认为 webroutes.xml，会自动搜索classpath下的webroutes.xml配置文件
    sessionIdCookieName  SESSIONID 采用的 COOKIE 名，默认为 JSESSIONID
    sessionIdCookiePath  输出 SESSIONID cookie 的路径，默认为空，表示当前目录
    expireSeconds 静态资源在浏览器中的的过期时间，单位：秒， 默认为0秒表示立即过期
    autoTrim 自动对所有参数值做trim, 默认为true
    
    sampleRate 全链路跟踪采样率, 实际比率为 1/sampleRate, 默认为1
    defaultSessionService  会话服务插件, 支持 memorysessionservice, jedissessionservice, 默认为memorysessionservice
                                     jedissessionservice插件参数：
                                     		clusterMode 是否是redis cluster, 默认为false
                                     		addrs 连接地址
                                     		expireSeconds 过期时间，默认为 600 秒
                                     		keyPrefix key前缀，默认为 KRW_
                                     		
    pluginParams  插件参数配置, 类型为List<String>, 所有需要配置参数的插件通过此参数进行配置，不需要配置参数的插件不用配置
                          每行配置一个插件，格式为 插件名:插件参数，  插件参数的格式自定义，标准风格是 a=b;c=d;...

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
    threads 当使用PUSH调用时, client可以作为server, 此时收到的请求在此线程池中运行, 
            默认为0由系统自动分配，可配置为-1不单独建立线程池，直接使用netty io线程；或>0的值
    maxThreads 同上，可配置一个大于threads的值，默认为0，也就是maxThreads=threads
    queueSize 同上，线程池中固定队列大小，默认为10000

    plugins 插件参数说明同server
    pluginParams  插件参数说明同server
                                          
## service

    id 名称 不填则会自动生成
    interfaceName 接口名, 必填
    impl 实现类的bean name, 如果在spring容器中，则可为空，自动根据interfaceName查找对应的bean
    transport 引用的server或webserver或client的id, 如果reverse=false, 
              则对应server或webserver的id; 如果reverse=true, 则对应client的id;
    reverse 正向调用还是逆向调用, 值为 true 或 false, 默认为 false
    registryNames 注册与发现服务名, 可填多个，用逗号隔开, 引用的是 registry的id
    group  注册与发现服务里的分组
    threads 服务级别的线程池配置参数, 含义同server, 默认为-1，不启用单独的线程池
    maxThreads 服务级别的线程池配置参数, 含义同server
    queueSize 服务级别的线程池配置参数, 含义同server
    
    每个service可配置0个或多个method在消息级别做配置

## referer

    id 名称 不填则会自动生成
    interfaceName 接口名, ，不可和serviceId同时使用
    serviceId 服务号 (http动态网关无接口类，根据服务号来配置)，不可和interfaceName同时使用
    transport  引用的client或server的id, 如果reverse=false, 则对应client的id; 如果reverse=true, 则对应server的id;
    reverse 正向调用还是逆向调用, 值为 true 或 false, 默认为 false
    direct 指定此参数可直连服务，无需通过注册与发现服务
    registryName  注册与发现服务名, 只能填一个
    group  册与发现服务里的分组
    timeout 超时时间, 毫秒，默认为3000
    retryCount 重试次数，默认为0
    loadBalance 负载均衡策略，可配置为 leastactive,roundrobin,random,hash,
                      leastactiveweight,roudrobinweight,randomweight 默认为roundrobin
                      hash插件可带参数，其他参数无配置参数
                      hash插件参数：hashField 指定要做hash的入参的参数名
    zip 压缩方式 0=不压缩 1=zlib 2=snappy
    minSizeToZip 启用压缩所需的最小字节数, 默认为10000
 
	breakerEnabled 是否开启动态熔断 默认为 false
	breakerWindowSeconds 熔断窗口 默认为 5 秒
	breakerWindowMinReqs 熔断需要的最少请求数 默认为20
	breakerCloseBy 熔断方式 1=按错误率(retCode非0即认为错误) 2=按超时率
	breakerCloseRate 熔断百分比 默认为 50 
	breakerSleepSeconds 熔断秒数， 默认为 5;
	breakerSuccMills 会恢复正常的请求毫秒数，超过此耗时不恢复，默认为 500毫秒
	breakerForceClose 是否强制熔断，默认为 false
	 
    每个referer可配置0个或多个method在消息级别做配置
 
## method	    

    pattern 消息匹配模式
      若第一个字符是数值，则表示是以消息ID作为匹配模式，格式示例：1-3,8,100-200,... 以逗号为分割符，以-来指定一段连续消息ID
      若第一个字符不是数值，则表示是以正则表达式来匹配消息名
    
    以下3个参数只用于referer
    timeout 消息级别的超时时间，毫秒，默认为3000
    retryCount 消息级别的试次数，默认为0
    
    以下4个参数只用于service
    threads 消息级别的线程池配置参数, 含义同server, 默认为-1，不启用单独的线程池
    maxThreads 消息级别的线程池配置参数, 含义同server
    queueSize 消息级别的线程池配置参数, 含义同server
    
## monitor
 
    accessLog 是否打印访问日志, 默认为true
    maskFields 日志里要屏蔽的字段，屏蔽后输出***代替原来的值
    maxRepeatedSizeToLog 对repeated参数, 输出前n项，否则日志会太大，默认为1
    logFormatter 日志格式，可选 simple, json， 默认为simple
    logThreads 异步输出日志的线程数，默认为1
    logQueueSize 异步输出日志的固定队列大小，默认为10000
    serverAddr 监控服务地址
    printDefault 是否输出protobuff消息里的默认值, 默认为false
    
# webroutes.xml配置				  

	启动webserver需要一个配套的webroutes.xml,  webroutes.xml 必须放在classpath目录下
	
	示例：
	
      <?xml version="1.0" encoding="utf-8"?>    
      <routes>    
      
          <import file="routes-b.xml"/>  

          <url hosts="*" path="/user/test1" methods="get,post" serviceId="100" msgId="1" 
               plugins="dummy" sessionMode="0"/>  
          <url hosts="*" path="/user/test2" methods="get,post" serviceId="100" msgId="2"/>  
          
          <group   prefix="/abc"  methods="get,post" serviceId="100">  
            <url path="/test3" msgId="3"/>  
            <url path="/test4" msgId="4"/>  
          </group>
      
          <dir hosts="*" path="/test1" staticDir="c:\ws\web\static" templateDir="c:\ws\web\template"/>  
                
      </routes>
      
  * 可通过import导入其它routes文件，这样可以按服务分别存放路由
  
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
        
        对插件的引用只能使用名称，不可带参数； 如参数需要参数，需在webserver的pluginParams里申明, 不带参数的插件不要申明，直接引用即可
        
  * group用来配置一组url公共的属性，简化url配置
  
        group节点不支持配置 path 和 msgId
        group节点允许配置的节点:
        
        hosts 允许的域名，*表示不限制，默认为*
        prefix 若配置了此值，则所有路径为 prefix + path, 默认为空
        methods 访问方法，支持get,post,head,put,delete, 默认为get,post; 
                如果post body是json格式，默认也会直接做解析，无需额外配置
        serviceId path对应的服务号
        plugins 用来配置插件名，允许多个，用逗号隔开
        sessionMode 会话模式 0=不需要会话 1=只需要会话ID 2=有会话则把会话信息传给后端，但不强制登录 2=必须要登录, 默认为0
  
  * 可通过dir定义静态资源目录，上传目录等

# HTTP通用网关参数映射
    
      webserver框架会自动将http里的http元信息，header,cookie,session,入参等映射到protobuff请求消息；
      webserver框架也会自动将protobuff响应消息映射到http里的http元信息,header,cookie,操作session, 输出内容等；
      通过以上的机制，webserver可以承担一个通用网关的功能，业务开发无需在http层再做开发，只需开发后台服务;
      webserver提供强大的扩展机制，业务可根据自己的需要开发必要的插件来实现一些特殊功能
      
      请求映射规则：
      
        按需映射，不关心的参数就不要在protobuffer消息里定义, 关心的参数按名称定义即可
        
        常规参数映射，按参数名映射到protobuffer消息里的参数名
        session 里的信息 -> 按参数名映射到protobuffer消息里的参数名 
                            (名称冲突则总是session里的优先, 客户端无法覆盖session参数)
        
        特殊参数映射，如果有需要，后端服务可以获取到http调用的所有细节, 一般建议不要去获取这些特殊信息
        
            session id -> sessionId
            http method -> httpMethod 值为 get,post,put,delete
            http schema -> httpSchema 值为 http,https
            http path -> httpPath 值为 http,https, 不含?后及以后的值
            http host -> httpHost 值为 header里的host值
            http query string -> httpQueryString 值为uri后?号以后的值
            http content-type -> httpContentType header里的content-type值, 去除;号以后的附加参数
            http content -> httpContent 值为http的content
            http header -> headerXxx 映射到protobuffer消息里以header开头的参数名, 需做名称转换，
                                     如 User-Agent，在pb里必须定义为headerUserAgent
            http cookie -> cookieXxx 映射到protobuffer消息里以cookie开头的参数名, xxx和cookie名严格保持一致，区分大小写
            
      响应映射规则：
        
        protobuff的消息里如带一些特殊参数，则会先做处理再从响应里删除再转换为json输出
        
        httpCode 单值 -> 会转换为实际的http code, 不设置则默认为200
        httpContentType 单值 -> 会转换为实际的http header里的content-type
        headerXxx 单值 -> 会转换为http输出的 header     
        cookieXxx 单值 -> 会转换为http输出的 cookie, cookie可带参数，格式为：值^key=value;key=value;...  
                  key支持 domain,path,maxAge,httpOnly,secure,wrap    
        session 不能是单值，必须是消息(Map) -> 
          如果成功完成登录，后端服务返回的session中应带 loginFlag=1, 以便框架在后续做登录检验；
          如 session 消息里带 loginFlag = 0 则会删除会话；否则将返回的session信息保存到会话上; 
          后续收到请求会自动将会话里的信息做登录验证，并把已登录信息转发给后端服务
          常规情况下后端服务不用去存储会话信息，也不用关心sessionId; 
          如果有特殊需求，后端也可以根据sessionId做自己的存储策略
        
        以上处理完毕后将剩余消息转换成json并输出, 如需控制输出格式或内容，可通过插件进行定制

# RPC调用超时配置

  * 所有的RPC调用都有3000毫秒的默认超时时间, 可通过3种方式修改超时时间
  
  * 修改referer级别配置, 指定服务级别的超时时间
  
  * 修改method级别配置, 指定消息级别的超时时间
  
  * 编程方式 
  
      在rpc调用前，增加一行代码：ClientContext.setTimeout(milliseconds); 
      
      ClientContext.setTimeout(1000); // 为下一个rpc调用设置超时时间为1秒, 每次都必须设置, rpc调用一发起就会清除此值
      LoginRes res = us.login(req); // 同步调用
  
# 客户端异步调用
        
        每个服务接口都有同步和异步两种形式, 如
            UserService.java  同步接口
            UserServiceAsync.java 异步接口, 异步接口的方法返回的是CompletableFuture<?>
          
        在客户端可以同时使用同步代理和异步代理 (启动方式不同获取动态代理方式也不同)
        获取到异步代理后，可以自由使用返回的future, 如：
        
        UserServiceAsync usa = app.getReferer("usAsync");
        
        1) 同时发出多个异步请求，等待所有返回

            CompletableFuture<LoginRes> f11 = usa.login(req1);
            CompletableFuture<LoginRes> f12 = usa.login(req2);
            f11.get();
            f12.get();
        
        2) 不阻塞，而是在future上设置回调函数
            
            CompletableFuture<LoginRes> f2 = usa.login(req); 
            f2.thenAccept( (res0) -> {
                log.info("in listener, resa="+res0.getRetCode()+","+res0.getRetMsg() );
            }
                        
        3) 发出请求后就不管了
        
            usa.login(req1);

        4) 同时发出多个请求，但只设置一个回调函数

              CompletableFuture<LoginRes> f5 = usa.login(req1);  // call async
              CompletableFuture<LoginRes> f6 = usa.login(req2);  // call async
              CompletableFuture<Void> f7 = f5.thenAcceptBoth(f6, (res1,res2) -> {
                  log.info("in listener, res1="+res1.getRetCode()+","+res1.getRetMsg() );
                  log.info("in listener, res2="+res2.getRetCode()+","+res2.getRetMsg() );
              });
						
        5) 同时发出多个异步请求，等待最快的一个返回
        
              CompletableFuture<LoginRes> f9 = usa.login(req2);  // call async
              CompletableFuture<Void> f10 = f8.acceptEither(f9, (res1) -> {
                  log.info("in listener, res first="+res1.getRetCode()+","+res1.getRetMsg() );
              });

        6) ...
        
        总之，可以充分享受java 8里姗姗来迟的CompletableFuture带来的异步编程体验
        
# 服务端异步实现

     以 UserService的LoginRes login(LoginReq req) 接口为示例：
     
     服务端同步实现方式或异步方式只能选择其一。
     
     同步实现方式：

          public LoginRes login(LoginReq req) {
              log.info("login received, peers="+ctx.getMeta().getPeers());
              return LoginRes.newBuilder().setRetCode(0)
                     .setRetMsg("hello, friend. receive req#"+i).build(); // 处理完直接返回
          }
	
	  异步实现方式：
	
          线程1：
          public LoginRes login(LoginReq req) {
              RpcClosure closure = ServerContext.closure(req); // closure 对象中有调用的所有上下文信息以及req信息
              // 将此closure对象传递到其它线程中或加入队列, 如 queue.offer(closure);
              return null; // 告诉框架此接口将异步实现
          }
          // closure 可以放心传递 closure, closure内仅仅包含一些普通的pojo对象
          
          
          线程2：
          // 其它线程获取到RpcClosure closure后
          closure.recoverContext(); // 每次跨线程传递closure后必须调用此接口恢复rpc上下文以及全链路跟踪trace上下文
          ... // 业务层处理
          LoginReq req = (LoginReq)closure.getReq(); // 获取入参
          log.info("login received, peers="+ctx.getMeta().getPeers());
          LoginRes res = LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#"+i).build();
          closure.done(res); // 什么时候获得了响应就调用done(res)函数
          
          closure对象可以在线程间不断传递，没有限制
                
# 服务端推送

    服务端启动：
    
        RpcApp app = new Bootstrap() 
        .addService(UserService.class,impl)  // 正常的 service
        .addReverseReferer("push",PushService.class) // 注意，这里加了referer
        .build();
    			
    客户端启动：
		
        RpcApp app = new Bootstrap() 
        .addReferer("us",UserService.class,"127.0.0.1:5600") // 正常的referer
        .addReverseService(PushService.class,impl)  // 注意，这里加了service, 需在客户端定义PushService的实现类
        .build();

    服务端推送代码：
		
      线程1：		
      RpcContextData ctx = ServerContext.get(); // 获取调用上下文，上下文中包含tcp连接标识connId
      String connId = ctx.getConnId(); // connId可以任意传递，保存到缓存中或持久化到db中
      
      线程2：
      // 从内存，缓存或db中获取到之前保存的connId
      ClientContext.setConnId(connId); // 推送前需要调用此函数确定此消息是推送到那个连接上
      PushReq req pushReqBuilder = PushReq.newBuilder().setClientId("123").setMessage("I like you").build();
      ps.push(req); // 完成推送

# 参数验证

	* 框架支持直接在proto文件中定义参数验证规则，简化程序中的程式化代码
	* 框架只会对请求对象进行参数验证，对结果对象不做验证
	* 参数验证的编写语法为protobuffer的标准语法, 示例：
	
		string s1 = 1  [  (krpc.vld).required = true  ] ;
		string s2 = 2  [  (krpc.vld).match="date" ] ;	
	    string s3 = 3  [  (krpc.vld).match="a.*c" ] ;			
		string s4 = 4  [  (krpc.vld) = {srange :"bbb,ccc"} ] ;
		repeated string s5 = 5  [  (krpc.vld) = { arrlen:"1,-"; values:"111,222" } ];
		
		一个field多个规则的时候protobuffer允许用两种等价形式编写规则：
		
			[  (krpc.vld).arrlen = "1,-", (krpc.vld).values = "111,222" ];
		    [  (krpc.vld) = { arrlen:"1,-"; values:"111,222" } ];
		
    * 支持的验证规则
    	
    	required 非空字符串, 适用于字符串
    	match 字符串必须符合某个规则, 适用于字符串和数值，转换为字符串后再匹配
    			int 必须是java int
    			long 必须是java long
    			double 必须是java double
    			date 必须是 2011-01-01 这种日期格式
    			timestamp  必须是 2011-01-01 12:12:11 这种时间戳格式
    			email 必须是电子邮件
    			其他  则认为是正则表达式
    	 values 用逗号隔开的多个枚举值, 适用于字符串和数值
    	 length 字符串长度, 适用于字符串和数值，转换为字符串后获取长度再比较
    	 	n  n个字符
    	 	n,m  仅n到m个字符
    	 	n,-  最少n个字符
    	 	-,n  最大为n个字符
    	 nrange 数值范围, 适用于字符串和数值，转换为数值后比较大小
    	 	n  min,max都为n
    	 	min,max 范围为min到max
    	 	min,-  最少min
    	 	-,max  最大max
    	 srange 字符串范围, 适用于字符串和数值，转换为字符串后比较大小
    	 	min,max 字符串范围为min到max
    	 arrlen 数组长度范围, 仅适用于repeated field
    	 	n  min,max都为n
    	 	min,max 范围为min到max
    	 	min,-  最少min
    	 	-,max  最大max

	 * 支持的验证规则目前不提供扩展机制
	 
# 打点和跟踪

    * krpc支持多种全链路跟踪系统, 可通过application配置参数 traceAdapter 来配置

         配置示例："traceAdapter"="skywalking:a=b;c=d;..." 冒号后的是插件参数，每个插件配置值可能不一样
         所有打点的信息都可以在全链路跟踪系统里查询到

    * 数据采集
    
    	 krpc的rpc框架本身已集成了全链路跟踪所需的各种打点，如果仅仅使用krpc的rpc功能无需配置探针
    	 只有对业务层或第三方框架（如httpclient, mybatis, hibernate等）才需要配置krpc的javaagent探针
    	 探针采用的是javasssit字节码技术，只需配置krpcsniffer.cfg文件即可采集数据，对业务层或第三方框架的代码零侵入
    
         建议业务层总是通过配置krpc的探针来采集数据， 确实有必要再手工通过代码打点采集数据

    * krpc探针配置

         要使用探针功能，需在启动应用程序的时候增加 java -javaagent:/path_to_sniffer_jar/krpc-sniffer-1.0.0.jar   your-main-class
         如果使用spring boot1/boot2,  则是 java -javaagent:/path_to_sniffer_jar/krpc-sniffer-1.0.0.jar  -jar  your-boot-application-jar
         在 krpc-sniffer-1.0.0.jar 的相同目录下，还必须存在 javassist-3.12.1.GA.jar， 否则探针无法加载
         探针会自动读取当前目录下的krpcsniffer.cfg配置文件, krpcsniffer.cfg配置文件格式如下：
         
         1) log.file 指定日志文件，未指定则为当前目录下的 krpcsniffer.log
         2) log.level 指定日志级别，未指定则为error (默认)，只支持 error, info两个级别
         3) 类名#方法名正则表达式=操作类别
         
         示例：
         
         log.level=info
         krpc.test.misc.TraceObj#say.*=DB 
         
         表示: 对 krpc.test.misc.TraceObj 类的匹配正则表达式(say.*)的方法自动增加探针，记录调用该方法的Span
         Span的type为DB, span的action为类名+消息名

     * 模型
    
       每次start开启一个新的Span并作为当前Span, 后续所有操作都针对该Span，直到stop, 每个span都有时间戳和耗时
       所有的Span组成一个树状结构
       可以使用startAsync开启一个新的Span但不作为当前Span
       
       每个span上可以增加event, event有时间戳但无耗时信息, 异常也作为event
       每个span上可以增加tag, tag就是普通的key/value信息
              
    * 业务层因只应使用krpc.trace.Trace静态类和krpc.trace.Span接口来进行打点
    
    * Trace类 此类都是静态方法，常用方法如下：
    
        void start(String type,String action) 可以嵌套，每次start后Span入栈，stop后出栈，后续所有操作都针对栈顶对象 
        long stop()
        long stop(boolean ok)
        long stop(String status)
        void logEvent(String type,String name)
        void logEvent(String type,String name,String result,String data)
        void logException(Throwable c)
        void logException(String message, Throwable c)
        void tag(String key,String value)
        void setRemoteAddr(String addr)
        
        Span startAsync(String type,String action)  异步调用，Span不入栈，后续用Span接口对该Span进行操作
        
     * Span接口 常用方法   
     
        long stop()
        long stop(boolean ok)
        long stop(String status)
        void logEvent(String type,String name)
        void logEvent(String type,String name,String result,String data)
        void logException(Throwable c)
        void logException(String message, Throwable c)
        void tag(String key,String value)
        void setRemoteAddr(String addr)

     * start/stop 配对
     
       如果start/stop之间可能抛出异常，应该如下:
       
       Trace.start(...)
       try {
         ...
       } finally {
         Trace.stop(...)
       }

     * type 参数规范 (暂定)
     
        DB 访问db
        REDIS 访问redis
        HTTP 访问http服务
     
     * status 参数规范 (暂定)     
     
        SUCCESS 成功
        ERROR 失败
      
     * 线程间Trace上下文传递
     
          跨线程Trace上下文如果不做处理，可能会造成调用链混乱，不会影响正常业务逻辑，但会造成全链路跟踪系统里的数据不正确
          
          在krpc框架中已经对Trace上下文做了集成处理  
          
              所有框架发起的调用，无需再手工设置trace上下文 
              业务层自己实现的线程, 只要调用过 closure.recoverContext(); Trace上下文就已经设置好了
              
          未使用krpc closure的情况 (比如一个后台服务，未使用krpc框架)
          
              线程1：调用Trace.currentContext() 获取当前trace上下文, 可以随意传递到其他线程
              线程2：调用Trace.setCurrentContext(ctx) 恢复trace上下文     
              
     * 进程间Trace上下文传递
     
         krpc框架已做了处理，业务层代码无需关心
 
# 负载均衡策略

		负载均衡策略在referer设置
		
		LeastActiveLoadBalance  最小活跃请求数，相同最小则随机
		LeastActiveWeightLoadBalance  带权重的LeastActive
		RoundRobinLoadBalance  轮询  (默认)
		RoundRobinWeightLoadBalance  带权重的RoundRobin
		RandomLoadBalance 随机
		RandomWeightLoadBalance  带权重的Random
		HashLoadBalance  根据某个入参进行hash取余
		
        带权重的版本需和动态路由插件 application.dynamicRoutePlugin 配合使用，否则等价于不带权重的版本
        内置插件不支持到method级别的负载均衡, 不过可自己实现插件支持
        
# 动态路由策略

		动态路由策略通过 application 的dynamicRoutePlugin 参数设置，每个应用只能设置一个插件
		krpc动态路由不要求必须与注册与发现服务绑定，非常灵活，如可以如下设置：
		   注册与发现使用 consul 插件，而动态路由可使用 consul 或 zookeeper 或  spring cloud config 或  其他的分布式配置
        DynamicRoutePlugin插件接口如下：
              DynamicRouteConfig getConfig(int serviceId,String serviceName,String group);
        插件返回的 DynamicRouteConfig 类包含的信息：
			int serviceId; // 服务号
			boolean disabled; // 是否强制对服务降级
			List<AddrWeight> weights; // 权重 addr weight   默认为100
			List<RouteRule> rules; // 规则   from to priority     	  
		       
       权重信息的 addr 可以是 ip:port形式，也可是 ip形式，也可带*统配符, *匹配0到n个数值
       规则 from 是一个由基本表达式组合而成的复合表达式
       			组合方式： 支持常见的并或非和括号表达式，分别使用  &&表示并  ||表示或  !表示非  ()表示括号，括号可以多层嵌套  
       			基本表达式的 格式为: key == values  或 key != values 
       			     key 只允许使用 application(当前应用名),host(当前主机的IP),msgId(要调用的消息号，如用于读写分离)
       			     values 为用逗号隔开的多个值, 可用*通配符
       			     == 的含义是 in, 任意一个值匹配即可
       			     != 的含义是 not in, 任意一个值都不可匹配     
       			from 可以为空，表示匹配所有
       			示例：
       				host == 192.168.3.1
       				host == 192.168.3.1,192.168.3.2
       				host == 192.168.3.*,192.168.4.*
       				host == 192.168.3.*,192.168.4.* && application == webgate
       				host == 192.168.3.*,192.168.4.* && host != 192.168.3.1*
       				msgId == 1,2,3
       规则 to 只能是基本表达式, 格式为 host == values  或 host != values 或 addr == values 或 addr != values
                host和addr都表示允许路由到哪些地址上去，host是按ip设定规则，addr是按ip:port设定规则
                values 为用逗号隔开的多个值, 可用*通配符, values中可使用特殊的$host表示本机IP
       			== 的含义是 in, 任意一个值匹配即可
       			!= 的含义是 not in, 任意一个值都不可匹配     
                values 可以为空，表示禁止所有路由
       规则的 priority 值越大越先匹配
       可以用路由规则来实现以下需求：
       		路由白名单
       		路由黑名单
       		排除灰度机器
       		只暴露部分机器
       		隔离不同机房网段机器
       		读写分离 按msgId配置规则
       		为重要应用提供额外的机器 按application配置规则
       		前后台分离 按application配置规则
       		同机部署服务，只访问本机的服务 使用 $host 配置规则

       krpc内置的consul, etcd, zookeeper,jedis 插件分别从以下的kv存储位置读取配置数据和配置数据的版本号
       consul:   
       		/v1/kv/dynamicroutes/{group}/{serviceId}/routes.json.version	值为版本号，版本号不变不会去读取routes.json
       		/v1/kv/dynamicroutes/{group}/{serviceId}/routes.json	值为 DynamicRouteConfig 序列化成json的字符串
       etcd:   
       		/v2/keys/dynamicroutes/{group}/{serviceId}/routes.json.version	值为版本号，版本号不变不会去读取routes.json
       		/v2/keys/dynamicroutes/{group}/{serviceId}/routes.json 值为 DynamicRouteConfig 序列化成json的字符串
		zookeeper:   
       		/dynamicroutes/{group}/{serviceId}/routes.json.version	  值为版本号，版本号不变不会去读取routes.json
       		/dynamicroutes/{group}/{serviceId}/routes.json 值为 DynamicRouteConfig 序列化成json的字符串	
		jedis:   
       		dynamicroutes.default.100.routes.json.version	  值为版本号，版本号不变不会去读取routes.json
       		dynamicroutes.default.100.routes.json  值为 DynamicRouteConfig 序列化成json的字符串	

# 熔断和降级

        krpc目前支持的只支持熔断后的降级(连接断开，强制降级，动态降级)，如果服务可以访问但出错不走降级策略。
        
		krpc支持以下几种熔断策略：
		
		1)  所有的长连接一旦断开，该地址会自动从路由中排除并走降级策略； 
		     krpc的实现是后台1秒1次去重连，重连成功后才会允许路由选择
		     
		2)  强制对服务降级： 若动态路由插件返回的 服务的 disabled = true, 则该服务所有请求不做路由直接走降级策略
		
		3)  动态熔断: 在referer 上开启 breakerEnabled = true, 则根据referer上设定的策略动态熔断和恢复，描述如下：
		
		        breakerEnabled 需要设置为 true 才会开启动态熔断
		        
		        在 breakerWindowSeconds 秒内请求数至少需要达到 breakerWindowMinReqs 个请求才会进入熔断状态判断
		        可按 超时率 (breakerCloseBy=2) 或 错误率(breakerCloseBy=1)判断是否是否需要熔断
		        若比率达到  breakerCloseRate 则熔断
		        熔断后该地址将会停止提供服务 breakerSleepSeconds 秒 
		        在 breakerSleepSeconds 秒后会每隔 breakerSleepSeconds 秒 放一个请求到该地址测试是否已恢复
		        如果测试请求返回成功(retCode==0)且耗时<=breakerSuccMills毫秒，则认为服务已恢复，解除熔断状态
		        
		        如果配置 breakerForceClose = true, 则不做判断直接认为已处于熔断状态
				
				以下是各参数的默认值：
					        
				boolean breakerEnabled = false ;
				int breakerWindowSeconds = 5;
				int breakerWindowMinReqs = 20;
				int breakerCloseBy = 1; // 1=errorRate 2=timeoutRate
				int breakerCloseRate  = 50; // 50% in 5 seconds to close the addr
				int breakerSleepSeconds = 5;
				int breakerSuccMills = 500;
				boolean breakerForceClose = false;		    
					
		熔断后的降级策略：
		
				用户可实现krpc.rpc.core.FallbackPlugin接口来自定义降级策略
				建议使用框架内置的 default 降级策略插件, 可以模拟成功或失败的任意结果
		
		default 降级策略插件：
		
				1) 自动查找classpath下的 fallback.yaml 文件，通过 fallback.yaml 可为每个消息配置策略
				2) fallback.yaml 示例：
				
					- { for: userservice.login,  match: userName ==abc && password == 123,  results: {retCode: 0, retMsg: abc}  }
					- { for: userservice.login,  match: , results: {retCode: -1000000, retMsg: abc, userId: 111}  }
					
				3) 	fallback.yaml 语法：
				
				    遵循 yaml 语法，能不用引号的地方都可以不用引号
				    整个fallback文件是一个数组, 数组每一项由3个属性组成：
				        for  针对哪个服务哪个消息，可以用服务名消息名形式，也可以用服务号消息号形式，每一项只能针对一个消息
				        match  条件表达式, 对同一个消息，可以设定不同的消息返回不同的内容, 此表达式和动态路由的from表达式语法类似
				                   插件会按顺序比较match是否一致，如果一致，则返回results对应的message, 否则继续比较下一项
				                   如果没有符合条件的项，则返回默认的错误码： -451  no connection
				                   
							       match 是一个由基本表达式组合而成的复合表达式
							       			组合方式： 支持常见的并或非和括号表达式，分别使用  &&表示并  ||表示或  !表示非 
							       			                ()表示括号，括号可以多层嵌套  
							       			基本表达式的 格式为: key operator values 
							       			     key 是request message的属性，支持嵌套消息，但不支持数组，如
							       			              a   a.b.c
							       			     operator 是操作符，目前支持的是  == !=  =~ !~ in not_in
							       			     values 是值，值的格式取决于operator， 不管入参类型，所有值都转成字符串再做比较
							       			     == !=, 值为字符串, 做字符串的完全匹配, 
							       			     =~ !~, 值为正则表达式，做正则匹配
							       			     in not_in , 值为逗号隔开的多个值

							       			match 可以为空或不写，表示匹配所有输入
							       			示例：
							       				a == abc
							       				a == abc || b == 123
							       				!(a == abc || b == 123) && c in 1,2,3
							       				
				        results  返回的对象,  results 为一个map，和该消息的response message完全对应，支持消息嵌套和数组
				                    可以只设定要返回的值，没有的值会自动使用默认值
		
		default 降级策略插件有一个file参数，可以用来指定非默认的yaml文件
				                   
# MOCK 测试

        当开发微服务时，如果依赖的服务端尚未完成，或需做不依赖外部的测试， 或希望测试特定的返回场景，
        都需要框架提供MOCK功能方便开发, 框架内置的 default fallback 插件除了做降级策略外，也可以用来满足此需求
		
		1) applicaiton config里的 fallbackPlugin 必须是 default
		2) 在 fallback.yaml里定义mock返回, 可根据不同输入提供不同输出
		3) 如果服务连不通，无需配置自动就会走fallback插件
		4) 如果服务能连通，可以强制在referer config设置 breakerEnabled = true, breakerForceClose = true 走fallback策略
		
		由于正式环境的降级策略和测试时的mock都需要编辑 fallback.yaml 文件，可以在测试时指定文件名参数使用不同文件
		
		fallbackPlugin="default:file=mock.yaml"  来使用 mock.yaml 作为 mock 文件
		
# 重试策略

       krpc通过 referer 或 method上的 retryCount 来控制重试次数
       retryCount = 0 不重试   (等价于 failfast 策略)
       retryCount > 0 则最多重试指定次数, 若累计时间已超时则不再重试 (等价于failover策略)
       
       krpc的重试指的是有可用服务的情况下对调用过程中(包括未发出，发送中，已收到响应)出现的错误进行重试
       如无可用服务，则直接走降级策略，不会走重试策略
       
       krpc的retry一定是会更换不同的服务器地址来重试，如无可用的候选服务器，则放弃重试
       对重试肯定会失败的错误也不会进行重试 （如编解码错误，参数验证错误等 )
       
# 启动和关闭

      krpc的启动支持2阶段启动 对应  init() start() 方法:
                init() 方法初始化但不打开端口，避免依赖的外部服务还不可用就对外提供服务, 也不对外注册；
                start() 方法打开对外接口, 对外进行注册等
                initAndStart()方法可将两步合成一步
                
      krpc的关闭支持2阶段关闭 stop() close() 方法，和启动阶段的 start() init() 方法对应
                stop() 方法可设置关闭标志，禁止接收外部新请求, 这时有新请求直接返回SERVER_SHUTDOWN错误
                          referer 如设置了retryCount>0，会自动改调用其它服务, 服务端的关闭可对客户端透明
                          stop() 方法如果没有被显式调用，也会在close()的时候被自动调用
                close() 方法进行真正的关闭动作；老的请求继续处理, 直到全部处理完毕
                stopAndClose()方法可将两步合成一步
                
      根据对krpc的使用方式大致可分为以下几种形式：
      
      1) 不使用 spring 框架 

          init,start,stop,close调用时机完全由用户代码自己控制
          krpc并未绑定shutdown hook, 如果进程退出时未调用close()方法进程会卡住无法关闭
          
      2) 使用spring java config
      
          建议定义rpcApp bean时只定义 init() 和 close() 方法, 但不调用start(), stop()方法
          在main函数里等容器完全启动后调用 start()，进程接收到退出信号后立即调用stop()再关闭spring容器
          自己创建的rpcApp bean若漏了close() 方法进程退出时会卡住无法关闭
          
          示例： krpc.test.rpc.javaconfig.server
                    krpc.test.rpc.javaconfig.client
                    
      3) 使用spring schema
          
          框架会自动创建rpcApp bean并绑定好init(),close()方法
          
		  使用 krpc:application的delayStart属性 可控制Spring启动时是否调用start()
		       	 delayStart=0 (默认) , 容器启动后自动调用start()
		       	 delayStart=n , 容器启动后通过定时器等待n秒后调用start()
		       	 delayStart<0, 用户需手工调用start()方法
		  框架不会自动调用stop()方法， 建议在进程接收到退出信号后立即调用stop()再关闭spring容器
          
          示例： krpc.test.rpc.schema
      
      4) 使用spring boot
		 
		  框架会自动创建rpcApp bean并绑定好init(),close()方法
		  
		  使用 krpc.application.delayStart 可控制SpringBoot启动时是否调用start()
		       	 delayStart=0 (默认) , 容器启动后自动调用start()
		       	 delayStart=n , 容器启动后通过定时器等待n秒后调用start()
		       	 delayStart<0, 用户需手工调用start()方法
		  框架不会自动调用stop()方法， 建议在进程接收到退出信号后立即调用stop()再关闭spring容器
          
          示例： misc\samples\boot1

# Web渲染插件
	  
	  框架默认的渲染格式为json, 如需渲染为其它格式，可通过内置的如下插件来配置：
	  
	    serverredirect插件，取结果中的redirectUrl值作为重定向的目标，通过302进行跳转
	    	<url ...    plugins="serverRedirect"/>  
	    	
	    html 插件，取结果中的html值作为输出内容, 需通过代码生成html内容
	    	<url ...    plugins="html"/>  
	    	
	    jsredirect插件，取结果中的redirectUrl值作为重定向的目标，通过js形式跳转
	    	<url ...    plugins="jsRedirect"/>  
	    	
	    plainText 插件，取结果中的plainText值作为输出内容
	    	<url ...    plugins="plainText"/>  
	    	
	    jsonp 插件, 将结果json串改成javascript, 形如callback(json)，可通过jsonp入参来修改callback函数
	    	<url ...    plugins="jsonp"/>
	    	
	    velocity插件，渲染为任意格式：
		    <dir hosts="*" path="/template1" templateDir="c:\ws\site\template"/>
		    <dir hosts="*" path="/template2" templateDir="classpath:vm"/>
	    	<url ...   plugins="velocity" template="a"/> 
            通过dir的templateDir来定义velocity模板所在的目录，可以是本地目录，也可以是classpath目录
            可通过url中的template属性来指定模板或结果中的template值(优先级更高)来指定模板
            
            模板文件名格式：模板文件名+后缀+vm
            文件的格式由后缀决定，可设置后缀为 .html  .xml  等
            在url中指定模板名的时候，无需带.vm后缀, 如果是html后缀，html也可忽略，其它格式不能忽略后缀
            模板如果在子目录中，需带子目录名，可多级

	       velocity插件参数:
	       		cache 是否启用cache, 默认为false
	       	    checkInterval 检查模板是否发生变化的间隔，单位秒，默认为10
	       	    version 人为设定的版本值，用于生成url
	       	    toolClass 人为设定的自定义工具类, 用于扩展模板功能
	       	    
	       velocity模板中如何取值：
	       
	           req.xxx  从入参中取值，支持多级嵌套, 和该消息的入参类型的属性
	           res.xxx  从结果中取值，支持多级嵌套, 和该消息的出参类型的属性
	           session.xxx  从会话信息中取值，支持多级嵌套
	           version 插件参数
	           tool  插件参数, 可通过tool调用各种辅助方法
                                        	
# 静态文件下载
	  
      在webroutes.xml中增加如下配置即可将web server作为静态文件服务器
      
          <dir hosts="*" path="/site" staticDir="c:\ws\site\static\"/>
          <dir hosts="*" path="/assets1" staticDir="classpath:assets1"/>
    	
    	  staticDir 可以是本地目录形式或者classpath:前缀的格式
    	  classpath:前缀的资源文件可以是在本地目录中，也可以在jar文件中，webserver每次启动的时候会自动将jar文件
    	  里的资源展开到 {application.dataDir}/jarcache 子目录下
    	  
      文件下载使用netty的zero copy技术, 不占内存。
      
      可通过配置webserver.expireSeconds控制静态资源在浏览器中的缓存时间，默认为0，不缓存

# 动态文件下载

      除了支持静态文件下载，也可通过程序来生成要下载的文件地址或直接输出二进制流
      
            方式1：动态生成本地文件路径下载
            
            	在protobuffer输出消息里设置以下属性，webserver就会自动启用文件下载
            	
            		string downloadFile 要下载的文件的本地路径,  此属性为特殊属性，只要存在就认为是一个文件下载的响应
            		                    此文件可以是已经存在的（增加了下载权限检查），也可能是动态生成的
            		int32 attachment 是否在header中增加 attachment 头，默认为0
            		int32 autoDelete 对动态生成的文件，是否在下载完毕后自动删除，默认为0
            
            方式2：直接输出二进制流
            
            	在protobuffer输出消息里设置以下属性，webserver就会自动启用二进制流下载功能
            	
            		bytes downloadString 要下载的二进制流, 类型必须是probuffer bytes,  
            		                        此属性为特殊属性，只要存在就认为要输出二进制流
            		string filename 浏览器中保存时的文件名, 可带中文名
          	    	       	    
# 文件上传
    
       krpc支持文件上传功能, 支持上传G级别的文件, 不占内存。
       
       在protobuffer输入消息里设置以下属性就会自动获取到文件上传的内容，映射关系如下：

               所有文件上传项会映射到如下类型的参数中
				message UploadFile {
					string file = 1;  // 上传后的文件保存在临时目录下，此值为临时文件的全路径名
					string filename = 2; // 原始文件名
					int64 size = 3; // 文件大小，允许为0
					string ext = 4; // 文件后缀，原始文件无后缀，此值可能为空
					string contentType = 5; // 文件类型, 可能为空
					string name = 6; // 表单中的名称
				}
				消息类型UploadFile和tag可以修改，消息里的属性名称不可以修改, 不感兴趣的值可不定义
				
				在输入消息如下定义九可以获取到上传的内容:
						UploadFile files = xxx;  // 如果只有一个文件上传项
						repeated UploadFile files = xxx; // 如果有多个文件上传项
				files为特殊属性，不可修改
				
				上传表单中的非文件属性按正常的定义消息属性就可以。
		
		上传的文件保存在临时目录下，需要由应用程序自行删除(程序中删除或定时清理)。
		临时目录为 {application.dataDir}/upload 子目录
		
		和上传相关的配置参数：
    		maxContentLength 最大包长，这个控制的是非文件上传的包大小
    		maxUploadLength 上传时允许最大长度(非精确字节数)，这个控制的是文件上传的大小
    		
    		
    				
		 
