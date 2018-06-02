
@echo off
rem rm.exe -fr target\

mkdir target\  2>>nul >>nul 
protoc-3.5.1.exe %1 --java_out=target --descriptor_set_out=%1.pb
touch.exe  %1.pb -r %1
