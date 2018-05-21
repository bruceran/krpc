
# 整体架构

	* 核心架构
	
      客户端业务层代码                                         服务端业务层代码 
	    -------------------------------------------------------------------------
	       启动时生成的动态代理
	       RpcClient            --->  注册与发现服务    <---         RpcServer
	       Cluster Manager
	       Netty4 Transport     --->   krpc codec      <---         Netty4 Transport
	    -------------------------------------------------------------------------
              网络层数据传输 <----  正向或逆向调用  ----> 网络层数据传输
	
	* 关系
		
		  一个进程内通常启动一个app
		  每个app内可以启动多个server,多个client,多个webserver
		  每个app内可启动多个service, service可绑定到不同的server, 或者client(PUSH调用)，或者绑定到webserver
		  每个app内可启动多个referer, referer可绑定到不同的client, 或者server(PUSH调用)
		  service和referer都可以在method级别做一些配置，如重试策略，线程池，流控参数等
		  每个app内可配置一个monitorservice做日志相关配置
		  每个app内可配置多个注册与服务插件，可同时连接多个注册与发现服务

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
      timeout int32 超时时间，客户端的超时时间可以传给服务端，服务端可以根据此信息丢弃已过期未执行的包
      compress int32 包体是否做了压缩以及压缩方式  0=不压缩 1=zlib 2=snappy
    
      目前服务号1已被框架使用，其中 serviceId=1 msgId=1 为心跳包, 心跳包无sequence
      
  包体, protobuff形式	
      
      框架对请求包无要求
      框架要求业务响应包里必须要有一个retCode来标识错误码
      传输时请求和响应包都可以不传

# 接口定义

	使用google proto文件来定义接口。
	
	示例proto文件；
	
		syntax="proto3";

		import "krpcbase.proto"; 
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
			option (serviceId) = 100;
			rpc login(LoginReq) returns (LoginRes)  { option (msgId) = 1; };
			rpc updateProfile(UpdateProfileReq) returns (UpdateProfileRes)  { option (msgId) = 2; };
		} 
  
  * 必须使用 syntax="proto3"
  * 必须使用 import "krpcbase.proto"; 来引入服务号消息号扩展, 否则生成的服务接口无法使用
  * 必须使用 option java_multiple_files = true; 保证生成的java类无嵌套，简化代码
  * 必须使用 option java_generic_services = true; 来根据service定义生成java接口, 否则只会生成输入输出类
  * 必须使用定制的protoc.exe文件来生成service接口，标准的protoc.exe根据service定义生成的java接口不能满足要求
  
	
	生成的接口：(此接口不用生成直接手写也可以)
	
      同步接口形式如下；(客户端和服务端通用)
	
        package com.xxx.userservice.proto;
        
        public interface UserService {
            LoginRes login(LoginReq req) ;
            UpdateProfileRes updateProfile(UpdateProfileReq req);
            
            static int serviceId = 100;
            static int loginMsgId = 1;
            static int updateProfileMsgId = 2;
        }
        
      异步接口形式如下；(仅用于客户端)
        
        package com.xxx.userservice.proto;
        
        import java.util.concurrent.CompletableFuture;
        
        public interface UserServiceAsync {
            CompletableFuture<LoginRes> login(LoginReq req) ;
            CompletableFuture<UpdateProfileRes> updateProfile(UpdateProfileReq req);
            
            static int serviceId = 100;
            static int loginMsgId = 1;
            static int updateProfileMsgId = 2;
        }
	
	使用krpc.bat文件来生成所有代码，后续可使用下列方式:
	
    * 将生成好的源码文件拷贝到项目的固定目录下即可使用
    * 若只想引用jar包也可拷贝jar包到项目依赖位置（本地目录或maven仓库）
    * 对http通用网关动态调用接口，会用到生成的 xxx.proto.pb 文件

# 约定

  * 所有业务层服务号从100开始
  
  * 所有消息号从1开始
  
  * 业务层错误码格式建议为： -xxxyyy  xxx为服务号 yyy为具体错误码，不同服务的错误码不同，如-100001 
  
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

	