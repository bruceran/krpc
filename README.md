# krpc

    krpc取名参考百度的brpc和google的grpc, k无特殊含义
    此框架使用java语言开发, 必须使用jdk 8才能使用此框架
    此框架可以用作后台服务开发，也可作为类似zuul的通用HTTP网关
    轻量，高性能，强大的扩展性

# 用户手册

## [Release Notes](doc/releasenotes.md) 

	  版本变更说明
	  和其它框架的简单比较

## [框架编译指南](doc/install.md) 

	  安装JDK 8
	  安装gradle 3.3
	  框架目录结构
	  框架依赖说明

## [开发指南](doc/develop.md)

	  整体架构
	  
	  krpc协议
	  接口定义
	  约定
	  
	  如何启动krpc
	  和spring框架集成(java config方式)
	  和spring框架集成(schema方式)
	  和spring框架集成(spring boot方式)

	  配置参数详解
	  routes.xml配置
	  HTTP通用网关参数映射

## [日志格式说明](doc/log.md) 

    日志文件种类
    日志文件格式
    日志输出控制

## [插件开发指南](doc/plugin.md) 

    spi扩展机制
    
    注册与发现插件
    监控服务插件
    loadbalance插件
    错误消息插件
    流量控制插件
    日志序列化插件
    HTTP网关插件
    HTTP网关会话服务插件
    HTTP网关json转换插件
