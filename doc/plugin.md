
# SPI扩展机制

	 krpc 使用java标准的spi机制来提供扩展点： 
	 通过在 META-INF/services/ 目录下创建以接口全限定名为名称的文件，并在文件中每行写入一个服务提供者的全限定名。
	 
	 krpc 框架内置的插件可查看 krpc jar包里的 META-INF/services/ 目录。
	 扩展SPI接口的类必须线程安全
	 
	 插件中要获取到Spring容器，可访问：
	 		BeanFactory bf = krpc.rpc.bootstrap.spring.SpringBootstrap.instance.spring;
     如果有这种需求，建议使用Spring Bean扩展机制
     
# Spring Bean 扩展机制

	 Spring容器中所有实现了SPI扩展接口的Bean都可以直接作为插件使用, 无需在META-INF/services/下进行定义
	 Spring容器中的Bean可以对一个实现类生成多个实例
	 扩展SPI接口的类必须线程安全

# 自定义Bootstrap类

     启动类必须继承  krpc.rpc.bootstrap.Bootstrap
     可以在启动程序前通过 -DKRPC_BOOTSTRAP=自定义启动类 或在 容器启动前使用 System.setProperty("KRPC_BOOTSTRAP","自定义启动类") 指定启动类
     Bootstrap 里创建的对象都可重载newXxx方法进行替换

# 插件查找和插件参数

	   在krpc的配置里，凡是可配置插件的地方，可通过以下方式指定插件名（不区分大小写）：
	       插件的前缀，如 roundrobin  (去掉插件的接口名SimpleName)
	       插件的类名，如 RoundRobinLoadBalance
	       插件的全限定类名，如 krpc.rpc.cluster.lb.RrLoadBalance
	       
	       对Spring Bean插件也一样
	
	   在指定插件名的同时可以用以下方式传递参数给插件：
	       插件名:插件参数, 第一个冒号前的被认为是插件名，第一个冒号后的被认为是插件参数，透明地传到插件的config方法
	   krpc的每个SPI插件都可实现一个config(String params)方法来获取外部参数，params参数格式由插件自己约定。
	   对Spring Bean 扩展的插件，无需使用上面的插件参数传递机制
	   
	   krpc默认的插件参数的风格是  k=v;k=v;...   以分号和等号做分隔符。  
	   routes.xml 里的插件要传递参数需在webserver.pluginParams里定义参数。
	
	   krpc的插件可以通过实现InitClose接口来在服务启动关闭时做初始化和清理工作。
	   如果插件是Spring bean形式的插件，则使用Spring的init,close机制做初始化和清理工作。
	   krpc所有插件的实现应该都是线程安全的。
	 
	   负载均衡插件  krpc.rpc.cluster.LoadBalance 接口
		       用来自定义loadbalance策略
		       通过RefererConfig.loadBalance配置
		       框架自带了roundrobin,random,leastactive,hash 插件

	   注册与发现插件  krpc.rpc.core.Registry 接口
		       用来自定义注册与发现机制
		       通过RegistryConfig配置
		       框架自带了 consul, etcd, zookeeper 插件

	   错误消息插件  krpc.rpc.core.ErrorMsgConverter 接口
		        用来自定义错误码错误消息转换方式
		        通过ApplicationConfig.errorMsgConverter配置
		        框架自带了 file (基于文件error.properties) 插件
		        		        
	   日志序列化插件  krpc.rpc.monitor.LogFormatter 接口
		       可对 access log 里的请求和响应实现自己的序列化格式
		       通过MonitorConfig.logFormatter配置
		       框架自带了 simple 和  json 插件

	   RPC插件 krpc.rpc.core.RpcPlugin 接口
		        可用来自定义流控策略
		        通过ClientConfig.plugins配置或通过ServerConfig.plugins配置
		        框架自带了 memory（单进程）和 jedis （分布式，依赖jedis） 插件
		       		       
	   WEB插件 krpc.rpc.web.WebPlugin 接口
		        可用来自定义流控策略
		        通过webroutes.xml配置

# HTTP网关插件

	  HTTP网关插件是指所有实现了 krpc.rpc.web.WebPlugin  接口的插件。
	  HTTP网关插件包含了一组可扩展接口，分别用于http请求处理的各个阶段，可按需扩展自己所需的接口。
	
	  krpc http请求的处理过程可分为如下阶段：

	            路由查找  根据url查找服务号消息号, 解析path中的变量，此阶段不支持扩展
	            
	            参数解析前同步处理  预留扩展点 (PreParseWebPlugin)
	            参数解析前异步处理  预留扩展点 (AsyncPreParseWebPlugin)
	            参数解析  此阶段将WebReq对象的queryString,content中的参数解析到parameters Map中，
	                      默认会解析form,json格式的参数, 此接口仅支持同步 (ParserWebPlugin)
	            参数解析后同步处理，预留扩展点 (PostParseWebPlugin)
	            参数解析后异步处理，预留扩展点 (AsyncPostParseWebPlugin)
	            
	            会话加载  此阶段将会话信息加载到WebContext的session Map里，
	                      如果路由配置里请求需要会话，才会加载会话信息，可通过会话服务插件扩展
	                      接口形式为异步， 可实现为同步或异步 (SessionService)
	            会话加载后同步处理，预留扩展点 (PostSessionWebPlugin)
	            会话加载后异步处理，预留扩展点 (AsyncPostSessionWebPlugin)

	            后台服务调用，此阶段会获取到WebRes对象，此阶段不支持扩展
	            
	            渲染前处理  预留扩展点，仅支持同步 (PreRenderWebPlugin)
	            渲染  此阶段将WebRes对象的 results Map转换为 content，默认渲染为json，
	                    可使用插件渲染为其它格式，仅支持同步 (RenderWebPlugin)
	            渲染后处理  预留扩展点，仅支持同步 (PostRenderWebPlugin)

	  一个普通的无会话无插件的请求处理流程会经过这样几个阶段：路由查找 -> 参数解析 ->  后台服务调用 ->  渲染   
	
	  HTTP网关插件分别提供了以下接口对应上述的各个阶段:

      		  krpc.rpc.web.PreParseWebPlugin  适用于参数解析前做签名校验，加解密等工作
      		  krpc.rpc.web.AsyncPreParseWebPlugin  同上，异步形式
      		  krpc.rpc.web.ParserWebPlugin 可自定义非标准入参的参数解析方式
      		  krpc.rpc.web.PostParseWebPlugin  适用于在对参数解析完毕后进一步处理
      		  krpc.rpc.web.AsyncPostParseWebPlugin  同上，异步形式
      		  krpc.rpc.web.SessionService 会话服务扩展接口, 用于会话信息的存储更新读取 
      		  				框架自带了 memorysessionservice（单进程）和 jedissessionservice （分布式，依赖jedis） 插件
      		  krpc.rpc.web.PostSessionWebPlugin  适用于在获取到会话信息后做进一步处理
      		  krpc.rpc.web.AsyncPostSessionWebPlugin  同上，异步形式
      		  krpc.rpc.web.PreRenderWebPlugin  适用于在渲染前调整map对象为符合要求的渲染数据格式
      		  krpc.rpc.web.RenderWebPlugin 可自定义非json输出的渲染插件，框架默认只会渲染为json格式
      		  krpc.rpc.web.PostRenderWebPlugin  适用于在对参数解析完毕后进一步处理

        每个网关插件必须实现WebPlugin接口，然后按需要选择实现上述的某一个或某几个接口
      	网关插件示例：krpc.rpc.web.impl.LogOnlyWebPlugin 此插件实现了上述所有接口

        对SessionService, 可通过在webserver配置好defaultSessionService 而无需每个路由上指定；
        如果想使用非默认的的策略，可通过路由的plugins参数引用一个SessionService来覆盖defaultSessionService
        
      	
      		  