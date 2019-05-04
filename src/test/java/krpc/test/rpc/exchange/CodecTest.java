package krpc.test.rpc.exchange;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class CodecTest {

    public static void main(String[] args) throws Exception {

        byte[] bb = new byte[] {1,2,3};
        ByteBuf b = Unpooled.wrappedBuffer(bb);
        byte[] bb2 = b.array();
        System.out.println(b.writerIndex());
        System.out.println(b.readerIndex());
        ByteBuf b2 = Unpooled.copiedBuffer(b);
        b.readerIndex(1);
        bb[0] = 4;
        System.out.println(b.writerIndex());
        System.out.println(b.readerIndex());
        System.out.println(b2.writerIndex());
        System.out.println(b2.readerIndex());

    }


}

