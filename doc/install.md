
# 安装JDK 8

	* 安装

	  安装Sun JDK 8, 需使用64位jdk版本

	* 设置JAVA_HOME和PATH环境变量

		JAVA_HOME 配置为JDK安装目录
		PATH中增加JDK安装目录下的bin目录为查找路径
	
		linux下示例：
		编辑.bash_profile
		    export JAVA_HOME=/usr/local/jdk1.8.0_101
		    export PATH=$JAVA_HOME/bin:$PATH
	
		在命令行下运行java -version, 若配置正确会显示版本信息

# 安装gradle 3.3 以上

  下载地址: https://gradle.org/releases/

	框架本身的编译使用gradle 3.3或以上版本; 默认配置文件里会用到阿里云仓库 
	如果要在spring boot 2.x下使用，需安装gradle 4.x版本，建议总是安装最新版本 的 gradle 

	* 下载 

		下载完毕后展开gradle目录即可
	
	* 设置GRADLE_HOME和PATH环境变量
	
		在PATH环境变量增加gradle安装目录下的bin目录为查找路径

		linux下示例：
		编辑.bash_profile
		    export GRADLE_HOME=/usr/local/gradle-3.3
		    export PATH=$GRADLE_HOME/bin:$PATH

# 框架目录结构

    README.md 本文件
    LICENSE  许可证
    build.gradle gradle配置文件
    src/ 所有源码
      main/
        java/
          krpc/
            common/ trace,httpclient,redis,rpc 组件共同依赖的文件，只有非常少的几个接口和类
            trace/  和rpc框架完全独立的调用链跟踪的trace框架, 可对接主流的zipkin,skywalking,cat等APM系统
            httpclient/  和rpc框架完全独立的http客户端
            redis/  和rpc框架完全独立的redis客户端
            rpc/    krpc框架本身
        resources/
          META-INF/
            services/ 框架支持的SPI接口
            spring.schemas  spring.handlers  krpc.xsd      spring自定义schema所需文件
            spring.factories             spring boot 所需文件
      test/
    doc/ 文档目录
    misc/  杂项
    	protos/  此目录下为krpc框架自己用到的一些protobuff文件以及所有的测试用的protobuff文件  
    	samples/  示例代码目录
    		sample1/ 不使用spring的示例
    		boot1/  使用spring boot 1.x的示例  
    		boot2/  使用spring boot 2.x的示例, 注意： 此工程要求gradle 4.x (spring boot 2.x的最低要求)才能编译运行
    	starters/ spring boot starter
    dist/
    dist/krpc-x.x.x.jar 编译后输出的krpc框架的allinone的jar文件, 不包括第三方依赖
    dist/tools/ protoc工具
                 changed_code.zip 修改后的源码文件以及diff文件, 原始文件位置：/src/google/protobuf/compiler/java
                 test.proto 用来测试工具的proto文件
    			 win/ windows版本下的工具
    			 linux/ lilux版本下的工具
    			 mac/ mac版本下的工具
	
    将源码下载到本地后运行:
    
    > gradle build 若无错误则表示编译成功
    > gradle install 将编译好后的jar包安装到本机的maven仓库
    > gradle upload 将编译好后的jar包上传到自己搭建的maven仓库，需先设置好build.gradle文件里的用户名和密码才能上传成功

# 框架外部依赖说明

  依赖项参见 build.gradle
  
	强依赖：缺少以下依赖框架无法编译和运行
  
        日志框架: 默认是使用logback框架
		compile 'org.slf4j:slf4j-api:1.7.22'  -- logback
		compile 'ch.qos.logback:logback-core:1.2.1'   -- logback
		compile 'ch.qos.logback:logback-classic:1.2.1'  -- logback
		
		若应用程序使用了其它日志框架，可自行加入以下jar包透明地转换到logback, 统一使用logback来做日志
		jcl-over-slf4j-1.6.6.jar   -- java common logging --> logback
		log4j-over-slf4j-1.6.6.jar   -- log4j -> logback
		
		compile 'com.google.protobuf:protobuf-java:3.5.1'   -- protobuff 支持
		compile 'io.netty:netty-all:4.1.16.Final'     -- netty 4
		compile 'javassist:javassist:3.12.1.GA'    -- 字节码生成

		json框架: 默认是使用jackson框架
            compile 'com.fasterxml.jackson.core:jackson-core:2.8.8'
            compile 'com.fasterxml.jackson.core:jackson-databind:2.8.8'
            compile 'com.fasterxml.jackson.core:jackson-annotations:2.8.8'
	
	可选依赖：
	
		网络包压缩；默认为不压缩, 除非配置使用snappy压缩才会用到以下依赖
			compile 'org.xerial.snappy:snappy-java:1.1.2.3'
		
		如果要使用jedis版本的流控服务或http会话服务需要用到jedis
            compile 'redis.clients:jedis:2.9.0'
				
		SPRING框架依赖；若使用krpc schema支持需用到以下依赖
			compile 'org.springframework:spring-core:4.1.6.RELEASE'
			compile 'org.springframework:spring-beans:4.1.6.RELEASE'
			compile 'org.springframework:spring-context:4.1.6.RELEASE'		
        
		SPRING BOOT依赖；若使用spring boot启动需用到以下依赖
			compile 'org.springframework.boot:spring-boot-autoconfigure:1.5.13.RELEASE'
        
# 框架包依赖关系

  * krpc.common 通用接口和实现类，所有模块依赖此包
  * krpc.trace 全链路跟踪API, 目前krpc.rpc模块依赖此模块
  * krpc.httpclient 基于netty4的http client
  * krpc.redis 基于netty4的redis client
  * krpc.rpc rpc框架本身
  
  * krpc.rpc.core.proto krpc协议头的proto生成的类文件
  * krpc.rpc.core krpc框架核心接口和类，依赖krpc.rpc.core.proto, 此模块大部分为接口, 所有其它模块都强依赖此包
  * krpc.rpc.util 框架内的辅助类
  * krpc.rpc.impl 实现krpc.rpc.core中的rpclient,rpcserver等核心功能
  * krpc.rpc.impl.transport 实现krpc.rpc.core中的网络层，codec等
  * krpc.rpc.cluster  krpc.rpc.core中的ClusterManager接口实现, 暴露loadbalance插件
  * krpc.rpc.cluster.lb   krpc.rpc.cluster中的LoadBalance接口实现
  * krpc.rpc.registry   krpc.rpc.core中的RegistryManager, Registry接口实现
  * krpc.rpc.web krpc框架HTTP核心接口和类,此模块大部分为接口
  * krpc.rpc.web.impl HTTP核心功能实现
  * krpc.rpc.monitor  krpc.rpc.core中的MonitorService接口实现
  
  * krpc.rpc.bootstrap 启动包，依赖所有上述包, 程序启动关闭只需依赖此包; 系统支持的所有配置参数可通过浏览此包下的XxxConfig类快速查看
  * krpc.rpc.bootstrap.spring Spring下的启动包，依赖所有上述包
  
# PROTOC工具安装及使用

  * 必须使用定制的protoc-3.5.1.exe文件来生成service接口，标准的protoc-3.5.1.exe根据service定义生成的java接口不能满足要求

  * dist/tool 目录下包含文件 changed_code.zip，此文件包括修改后的源文件以及git diff内容，复制4个文件到 /src/google/protobuf/compiler/java 再运行pb的make程序就可以编译出定制版本的protoc可执行程序

  * 修改点： 1) 生成的接口形式（同步接口和异步接口） 2) 输入输出类，对string类型的入参允许 null, null参数不报异常而是设置为默认值
  
  * 将本项目的 dist/tool 目录加入path环境变量, 输入命令：protoc-3.5.1.exe --version  看到有输出 "libprotoc 3.5.1" 字样表示成功
  
  * 编译proto文件的命令： krpc.bat  yourprotofile.proto  此脚本每次只能处理一个文件
  
  * 生成的java文件放在target子目录下，同时会在proto文件相同目录生成一个时间戳完全一致的 yourprotofile.proto.pb (此文件只用于动态http网关，一般不用)
  
  * bin目录下附带了一个简单的 test.proto, 可进入此目录，输入krpc.bat test.proto查看示例输出文件
