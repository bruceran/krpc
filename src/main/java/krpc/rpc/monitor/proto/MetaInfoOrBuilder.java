// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: krpcmonitor.proto

package krpc.rpc.monitor.proto;

public interface MetaInfoOrBuilder extends
    // @@protoc_insertion_point(interface_extends:MetaInfo)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * 1=serviceId 2=serviceId.msgId  3=errorCode
   * </pre>
   *
   * <code>int32 type = 1;</code>
   */
  int getType();

  /**
   * <code>string value = 2;</code>
   */
  java.lang.String getValue();
  /**
   * <code>string value = 2;</code>
   */
  com.google.protobuf.ByteString
      getValueBytes();

  /**
   * <code>string text = 3;</code>
   */
  java.lang.String getText();
  /**
   * <code>string text = 3;</code>
   */
  com.google.protobuf.ByteString
      getTextBytes();
}
