
rm -fr target/

mkdir -p target/src  
protoc-3.5.1 $1.proto --java_out=target/src --descriptor_set_out=$1.proto.pb
touch  $1.proto.pb -r $1.proto
jar cf $1_sources.jar -C target/src/ .
touch  $1_sources.jar -r $1.proto

javapackagepath=`grep java_package $1.proto|awk -F\" '{print $2}'|sed 's/\./\//g'`

mkdir target/classes  
javac -cp "protobuf-java-3.5.1.jar:krpc-0.1.0.jar:*:." -d target/classes/ target/src/$javapackagepath/*.java
jar cf $1_protos.jar -C target/classes/ .
touch  $1_protos.jar -r $1.proto


