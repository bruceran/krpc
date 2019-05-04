
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

# 安装maven或gradle

    由于gradle发布到maven仓库时生成的pom文件缺少optional属性，会导致依赖krpc的项目导入不必要的依赖，
    故同时提供maven配置和gradle配置，gradle配置仅用于本地源码编译，不用于发布; 正式发布请使用maven。

	* maven 安装
	    
	    下载地址: http://maven.apache.org/download.cgi
	    下载完毕后展开并设置M2_HOME和PATH环境变量

		linux下示例：
		编辑.bash_profile
		    export M2_HOME=/usr/local/maven-3.5.3
		    export PATH=$M2_HOME/bin:$PATH
	
        运行 >mvn install 若无错误则表示编译成功
        运行 >mvn deploy 上传jar包到自己搭建的私服，需先设置私服的用户名和密码才能上传成功
	    
	    进入misc/springbootstarter/
	    运行 >mvn install 完成对starter的编译和本地安装,   运行misc/samples/boot1  misc/samples/boot2需要此依赖
	    		
	* gradle 安装
	    
	     gradle 需要至少 3.3 以上, 如果要在spring boot 2.x下使用，需安装gradle 4.x版本，建议总是安装最新版本 的 gradle
	    下载地址: https://gradle.org/releases/
	    下载完毕后展开并设置GRADLE_HOME和PATH环境变量

		linux下示例：
		编辑.bash_profile
		    export GRADLE_HOME=/usr/local/gradle-3.3
		    export PATH=$GRADLE_HOME/bin:$PATH

		运行 >gradle build 若无错误则表示编译成功
    
# 框架目录结构

    README.md 本文件
    LICENSE  许可证
    build.gradle gradle配置文件
    pom.xml maven配置文件
    src/ 所有源码
      main/
        java/
          krpc/
            common/ trace,httpclient,rpc 组件共同依赖的文件，只有非常少的几个接口和类
            httpclient/  和rpc框架完全独立的http客户端
            persistqueue 基于文件的持久化队列
            rpc/    krpc框架本身
            trace/  和rpc框架完全独立的调用链跟踪的trace框架, 可对接主流的zipkin,skywalking,cat等APM系统
        resources/
          META-INF/
            services/ 框架支持的SPI接口
            spring.schemas  spring.handlers  krpc.xsd      spring自定义schema所需文件
            spring.factories             spring boot 所需文件
      test/
    doc/ 文档目录
    misc/  杂项
    	sniffer/ krpc的探针代理源码
    	springbootstarter/ spring boot starter, 仅一个pom文件，spring boot 1.x / 2.x  starter通用
    	monitorserver 每个krpc可以上报数据给服务端，此模块为monitor服务端的框架代码, 暂无实际逻辑
    	protos/  此目录下为krpc框架自己用到的一些protobuff文件以及所有的测试用的protobuff文件  
    	samples/  示例代码目录
    		sample1/ 不使用spring的示例
    		boot1/  spring boot 1.x的示例  
    		boot2/  注意： 此工程要求gradle 4.x (spring boot 2.x的最低要求)才能编译运行
    		boot2client/  boot2对应的一个客户端，对外提供http服务，通过发现机制查找boot2

    dist/
    dist/sniffer 使用krpc的探针所需的jar包和配置文件
    dist/tool/ 
	          install.txt pb工具安装说明
			  test/  编译工具测试文件， test.proto 用来测试工具的proto文件
			  protoc_changed_code/ 修改过的pb源码， 原始文件位置：/src/google/protobuf/compiler/java

# 框架外部依赖说明

  依赖项参见 build.gradle 或 pom.xml 
  
	强依赖：缺少以下依赖框架无法编译和运行
  
		compile 'org.slf4j:slf4j-api:1.7.22'  -- logback
		compile 'ch.qos.logback:logback-core:1.2.1'   -- logback
		compile 'ch.qos.logback:logback-classic:1.2.1'  -- logback
		compile 'com.google.protobuf:protobuf-java:3.5.1'   -- protobuff 支持
		compile 'com.google.protobuf:protobuf-java-util:3.5.1'   -- protobuff json 支持
		compile 'io.netty:netty-all:4.1.16.Final'     -- netty 4
		compile 'javassist:javassist:3.24.0.GA'    -- 字节码生成
		compile 'com.fasterxml.jackson.core:jackson-core:2.8.11'   -- json框架
		compile 'com.fasterxml.jackson.core:jackson-databind:2.8.11.3'   -- json框架
		compile 'com.fasterxml.jackson.core:jackson-annotations:2.81'   -- json框架
		compile 'org.yaml:snakeyaml:1.17'   -- 解析yaml格式的fallback配置文件

	可选依赖：
	
		网络包压缩；默认为不压缩, 除非配置使用snappy压缩才会用到以下依赖
			compile 'org.xerial.snappy:snappy-java:1.1.2.3'
		
		如果要使用jedis版本的流控服务/http会话服务/注册与发现插件, 需要用到jedis
			compile 'redis.clients:jedis:2.9.0'   
            导入此jar包还会传递导入 commons-pool2
		
		如果要使用zookeeper版本的注册与发现插件
			compile ('org.apache.curator:curator-framework:2.12.0')  
			导入此jar包还会传递导入 guava, zookeeper, curator-client
				
		如果要使用velocity模板
			compile 'org.apache.velocity:velocity:1.7'

		SPRING框架依赖；若使用krpc schema支持需用到以下依赖
			compile 'org.springframework:spring-core:5.0.7.RELEASE'
			compile 'org.springframework:spring-beans:5.0.7.RELEASE'
			compile 'org.springframework:spring-context:5.0.7.RELEASE'
        
		SPRING BOOT依赖；若使用spring boot启动需用到以下依赖
			compile 'org.springframework.boot:spring-boot-autoconfigure:1.5.13.RELEASE'
        
# 框架包依赖关系

  * krpc.common 通用接口和实现类，所有模块依赖此包
  * krpc.trace 全链路跟踪API, 目前krpc.rpc模块依赖此模块
  * krpc.httpclient 基于netty4的http client
  * krpc.persistqueue 基于文件的持久化队列
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
  * krpc.rpc.bootstrap.spring Spring下的启动类，依赖所有上述包
  * krpc.rpc.bootstrap.springboot Spring boot下的启动类，依赖所有上述包
  
# PROTOC工具安装及使用

  * 必须使用定制的protoc编译命令来生成java类和java接口，标准的protoc 命令生成的java接口和java类不能满足要求
  
        Windows下： protoc-3.5.1.exe    放入path路径中以便c.sh脚本能找到此命令
		linux/mac下：protoc-3.5.1       放入path路径中以便c.sh脚本能找到此命令, 若生成的可执行文件protoc， 可手工建一个软链

  * 从源码编译 (cygwin/linux/mac)
	  
		git clone https://github.com/bruceran/protobuf.git
		cd protobuf
		git checkout v3.5.1-krpc  # 必须使用此分支，其它分支不支持krpc
		
		./autogen.sh
		./confgure
		make
		make install
		
  * 3.5.1版本的源码修改点： 
          1) 生成的接口形式（同步接口和异步接口） 
		  2) 输入输出类，对string类型的入参允许 null, null参数不报异常而是设置为默认值
		  3) 输出类增加ok,failed方法简化相应类的生成
		
  * 使用方式
  
        cygwin/linux/mac下先确认 可通过命令行直接访问 protoc-3.5.1 了
				输入命令：  protoc-3.5.1 --version  看到有输出 "libprotoc 3.5.1" 字样表示成功
  
		进入 test 目录，运行 ./c.sh   test  若无报错则表示安装成功， 成功后可生成 test_protos.jar  test_sources.jar  test.proto.pb 3个文件
		test_protos.jar  用于项目导入，加入此jar包后就可以直接进行开发了
		test_sources.jar  test_protos.jar 对应的源文件，不用可修改c.sh不生成
		test.proto.pb  对应的pb文件，仅用于http网关不使用test_protos.jar做动态路由，不用可修改c.sh不生成
