
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

# 安装gradle 3.3

	框架本身的编译使用gradle 3.3或以上版本; 默认配置文件里会用到阿里云仓库 

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
	doc/ 文档目录
	dist/ 编译后的jar包, pb工具, 依赖的jar包

# 框架依赖说明

  依赖项参见 build.gradle
  
	强依赖：缺少以下依赖框架无法编译和运行
  
		compile 'org.slf4j:slf4j-api:1.7.22'  -- logback
		compile 'ch.qos.logback:logback-core:1.2.1'   -- logback
		compile 'ch.qos.logback:logback-classic:1.2.1'  -- logback
		
		框架自身的log框架是logback, 若应用程序使用了其它日志框架，可自行加入以下jar包透明地转换到logback, 统一使用logback来做日志
		jcl-over-slf4j-1.6.6.jar   -- java common logging --> logback
		log4j-over-slf4j-1.6.6.jar   -- log4j -> logback
		
		compile 'com.google.protobuf:protobuf-java:3.5.1'   -- protobuff 支持
		compile 'io.netty:netty-all:4.1.16.Final'     -- netty 4
		compile 'javassist:javassist:3.12.1.GA'    -- 字节码生成
	
	可选依赖：
	
		HTTP网关的json转换功能: 默认插件是jackson插件, 需要以下2个依赖; 如果服务只启动tcp功能，未启动http网关，则不用以上依赖
			compile 'com.fasterxml.jackson.core:jackson-core:2.8.8'
			compile 'com.fasterxml.jackson.core:jackson-databind:2.8.8'

		网络包压缩；默认为不压缩, 除非配置使用snappy压缩才会用到以下依赖
			compile 'org.xerial.snappy:snappy-java:1.1.2.3'
		
		SPRING框架依赖；若使用krpc schema支持需用到以下依赖
			compile 'org.springframework:spring-core:4.1.6.RELEASE'
			compile 'org.springframework:spring-beans:4.1.6.RELEASE'
			compile 'org.springframework:spring-context:4.1.6.RELEASE'		

# PROTOC工具安装

  * 必须使用定制的protoc-3.5.1.exe文件来生成service接口，标准的protoc-3.5.1.exe根据service定义生成的java接口不能满足要求
  
  * 将本项目的dist/tool 目录加入path环境变量, 输入命令：protoc-3.5.1.exe --version  因看到有输出 "libprotoc 3.5.1" 字样
  
  * 编译命令： krpc.bat  yourprotofile.proto  此脚本每次只能一个文件
  
  * 生成的java文件放在target子目录下，同时会在proto文件相同目录生成一个时间戳完全一致的 yourprotofile.proto.pb (此文件只用于动态http网关，一般不用)
  
  * bin目录下附带了一个简单的 test.proto, 可进入此目录，输入krpc.bat test.proto查看输出的文件
  
