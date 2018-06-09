
# SPI 扩展机制

	 krpc 使用java标准的spi机制来提供扩展点： 
	 通过在 META-INF/services/ 目录下创建以接口全限定名为名称的文件，并在文件中每行写入一个服务提供者的全限定名。
	 
	 krpc 框架内置的插件可查看 krpc jar包里的 META-INF/services/ 目录。

# Spring Bean 扩展机制

	 Spring容器中所有实现了SPI扩展接口的Bean都可以直接作为插件使用, 无需在META-INF/services/下进行定义

# 插件加载和插件参数

	   在krpc的配置里，凡是可配置插件的地方，可通过以下方式指定插件名（不区分大小写）：
	       插件的前缀，如 rr
	       插件的类名，如 RRLoadBalance
	       插件的全限定类名，如 krpc.rpc.cluster.lb.RrLoadBalance
	
	   在指定插件名的同时可以用以下方式传递参数给插件：
	       插件名:插件参数, 第一个冒号前的被认为是插件名，第一个冒号后的被认为是插件参数，透明地传到插件的config方法
	   krpc的每个SPI插件都可实现一个config(String params)方法来获取外部参数，params参数格式由插件自己约定。
	   
	   krpc默认的插件参数的风格是  k=v;k=v;...   以分号和等号做分隔符。  
	   routes.xml 里的插件不支持  插件名:插件参数 方式来配置插件参数，而必须单独使用plugin来定义参数。
	
	   krpc的插件可以通过实现InitClose接口来在服务启动关闭时做初始化和清理工作。
	   krpc所有插件的实现应该都是无状态的。
	   krpc插件都是由框架创建的，暂不支持spring的属性注入，插件可在init方法里手工从spring获取bean完成初始化。
	 
	   loadbalance插件  krpc.rpc.cluster.LoadBalance接口
		       用来自定义loadbalance策略
		       框架自带了rr,random,hash,responsetime 插件
		       
	   注册与发现插件  krpc.rpc.core.Registry接口
		       用来自定义注册与发现机制
		       框架自带了 consul, etcd, zookeeper, eureka 插件

	   流量控制插件 krpc.rpc.core.FlowControl 接口
		        用来自定义流控策略
		        框架自带了 memory（单进程）和 jedis （分布式，依赖jedis） 插件
		       
	   错误消息插件  krpc.rpc.core.ErrorMsgConverter 接口
		        用来自定义错误码错误消息转换方式
		        框架自带了 file (基于文件error.properties) 插件
		        		        
	   日志序列化插件  krpc.rpc.monitor.LogFormatter 接口
		       可对 access log 里的请求和响应实现自己的序列化格式
		       框架自带了 simple 和  jackson 插件

# HTTP网关插件

	  HTTP网关插件并非一个插件，而是包含了一组插件，分别用于http请求处理的各个阶段。
	
	  krpc http请求的处理过程可分为如下阶段：

	            路由查找  根据url查找服务号消息号, 解析path中的变量
	            流控   预留扩展点，可通过流控插件扩展， 可同步或异步
	            参数解析前处理 预留扩展点，仅支持同步
	            参数解析  此阶段将WebReq对象的queryString,content中的参数解析到parameters Map中，
	                      默认会解析form,json格式的参数，仅支持同步
	            参数解析后处理，预留扩展点， 可同步或异步
	            会话加载  此阶段将会话信息加载到WebContext的session Map里，
	                      如果路由配置里请求需要会话，才会加载会话信息，可通过会话服务插件扩展， 可同步或异步
	            会话加载后处理，预留扩展点,  可同步或异步
	            后台服务调用，此阶段会获取到WebRes对象
	            渲染前处理  预留扩展点，仅支持同步
	            渲染  此阶段将WebRes对象的 results Map转换为 content，默认渲染为json，可使用插件渲染为其它格式，仅支持同步
	            渲染后处理  预留扩展点，仅支持同步

	  一个普通的无会话无插件的请求处理流程会经过这样几个阶段：路由查找 -> 参数解析 ->  后台服务调用 ->  渲染   
	
	  HTTP网关插件共提供2个SPI扩展接口:
	  
	    1) krpc.rpc.web.SessionService 会话服务扩展接口, 用于会话信息的存储更新读取 
		     框架自带了 memory（单进程）和 jedis （分布式，依赖jedis） 插件, 此插件的配置直接在WebServer的配置里进行配置
	  
	    2) krpc.rpc.web.WebPlugin  标记接口，所有HTTP网关插件必须实现此标记接口才会被加载
	    
    	      WebPlugin的实现类可以实现以下的扩展接口对请求处理流程进行扩展，可以在一个类中同时实现多个接口：
    
      		  krpc.rpc.web.PreParseWebPlugin  适用于参数解析前做签名校验，加解密等工作
      		  krpc.rpc.web.ParseWebPlugin 可自定义非标准入参的参数解析方式
      		  krpc.rpc.web.PostParseWebPlugin  适用于在对参数解析完毕后进一步处理
      		  krpc.rpc.web.AsyncPostParseWebPlugin  同PostParseWebPlugin，但接口形式为异步形式
      		  krpc.rpc.web.PostSessionWebPlugin  适用于在获取到会话信息后做进一步处理
      		  krpc.rpc.web.AsyncPostSessionWebPlugin  同PostSessionWebPlugin，但接口形式为异步形式
      		  krpc.rpc.web.PreRenderWebPlugin  适用于在渲染前调整map对象为符合要求的渲染数据格式
      		  krpc.rpc.web.RenderWebPlugin 可自定义非json输出的渲染插件，框架默认只会渲染为json格式
      		  krpc.rpc.web.PostRenderWebPlugin  适用于在对参数解析完毕后进一步处理

      		  