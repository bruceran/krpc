# krpc

    krpc取名参考百度的brpc和google的grpc, k无特殊含义
    此框架使用java语言开发, 必须使用jdk 8才能使用此框架
    轻量，高性能，强大的扩展性

# 用户手册

## [框架设计杂谈](doc/talk.md)  

## [Release Notes](doc/releasenotes.md) 
    
    版本变更说明
    和其它框架的基础特性比较

## [框架编译指南](doc/install.md) 

    安装JDK 8
    安装gradle 3.3 以上
    框架目录结构
    框架外部依赖说明
    框架包依赖关系	  
    PROTOC工具安装及使用	  
	  
## [开发指南](doc/develop.md)
    
    整体架构
    
    krpc协议
    接口定义
    服务号和错误码约定
    
    如何启动krpc
    和spring框架集成(java config方式)
    和spring框架集成(schema方式)
    和spring框架集成(spring boot方式)
    
    配置参数详解
    routes.xml配置
    HTTP通用网关参数映射
    RPC调用超时配置
    客户端异步调用
    服务端异步实现
    服务端推送
    自定义插件如何获取到Spring容器
    如何进行业务层打点
    参数验证
    
## [日志格式说明](doc/log.md) 
    
    日志文件种类
    日志文件格式
    日志输出控制

## [插件开发指南](doc/plugin.md) 

    SPI扩展机制
    插件加载和插件参数
    HTTP网关插件


