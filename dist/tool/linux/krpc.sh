
#rm -fr target/

mkdir target/  1>>/dev/null 2&>1
protoc-3.5.1 $1 --java_out=target --descriptor_set_out=$1.pb
touch  $1.pb -r $1
