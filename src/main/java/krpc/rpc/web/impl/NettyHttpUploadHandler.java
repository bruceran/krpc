package krpc.rpc.web.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import krpc.common.Json;
import krpc.rpc.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NettyHttpUploadHandler extends ChannelInboundHandlerAdapter {

    static Logger log = LoggerFactory.getLogger(NettyHttpUploadHandler.class);

    static byte CR = '\r';
    static byte NL = '\n';

    long maxUploadLength = 5000000;
    String uploadDir;

    HttpMessage tempMessage = null;
    int tempUploaded = 0;
    ByteBuf lastContent = Unpooled.buffer(50000);
    List<Map<String, String>> data = new ArrayList<>();
    byte[] boundary = null;
    String tempFile = null;
    OutputStream tempFileOutputStream = null;

    boolean closed = false;

    NettyHttpUploadHandler(String uploadDir, long maxUploadLength) {
        this.uploadDir = uploadDir;
        this.maxUploadLength = maxUploadLength;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {

        boolean needRelease = false;
        try {
            needRelease = channelRead0(ctx, msg);
        } catch (Exception e) {
            log.error("upload file read exception, exception=" + e.getMessage());
            closed = true;
            ctx.close();
        } finally {
            if (needRelease) ReferenceCountUtil.release(msg);
        }

    }

    boolean channelRead0(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (closed) return true;

        if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;
            String contentType = m.headers().get(HttpHeaderNames.CONTENT_TYPE);
            if (contentType != null && contentType.indexOf("multipart/form-data") >= 0) {  // && HttpUtil.isTransferEncodingChunked(m)
                HttpUtil.setTransferEncodingChunked(m, false);
                this.tempMessage = m;
            } else {
                this.tempMessage = null;
                ctx.fireChannelRead(msg);
            }
            return false;
        }

        if (msg instanceof HttpContent) {
            if (tempMessage == null) {
                ctx.fireChannelRead(msg);
                return false;
            }

            HttpContent chunk = (HttpContent) msg;
            ByteBuf content = chunk.content();
            int len = content.readableBytes();

            if (tempUploaded + len > maxUploadLength) {
                closeTempFile();
                new File(tempFile).delete();
                reset();
                throw new RuntimeException("HTTP content length exceeded " + maxUploadLength + " bytes.");
            }

            tempUploaded += len;

            if (tempFile == null) {
                lastContent.writeBytes(content);
            } else {
                if (lastContent.readableBytes() == 0) {

                    ByteBuf bak = lastContent;
                    lastContent = content;

                    boolean finished = readMultiPartFile();
                    lastContent = bak;
                    if (content.readableBytes() > 0) {
                        lastContent.writeBytes(content);
                    }

                    if (!finished) {
                        return true;
                    }
                } else {
                    lastContent.writeBytes(content);

                    boolean finished = readMultiPartFile();
                    if (!finished) {
                        lastContent.discardReadBytes();
                        return true;
                    }
                }
            }

            if (boundary == null) {
                boundary = parseBoundary();
                if (boundary == null) {
                    if (lastContent.readableBytes() >= 200) {
                        throw new RuntimeException("HTTP boundary not found in first 200 bytes");
                    }
                    return true;
                }
            }

            while (readNextPart()) {
            }

            lastContent.discardReadBytes();

            if (chunk instanceof LastHttpContent) {
                String json = generateJson();

                ByteBuf bb = ByteBufUtil.writeUtf8(ctx.alloc(), json);
                tempMessage.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
                tempMessage.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bb.readableBytes());
                ctx.fireChannelRead(tempMessage);
                DefaultLastHttpContent c = new DefaultLastHttpContent(bb);
                ctx.fireChannelRead(c);

                reset();
            }

            return true;
        }

        ctx.fireChannelRead(msg);

        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    String generateJson() {

        // data =[
        // {"name":"a","value":"aaa"},
        // {"name":"b","value":"bbb"},
        // {"filename":"","file":"./upload/4ec237f877eb48eb947ef136c63ea837.tmp","name":"file1","contentType":"application/octet-stream"},
        // {"filename":"","file":"./upload/9897d00959394a20939e8691101bc6a5.tmp","name":"file2","contentType":"application/octet-stream"}
        // ]

        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        for (Map<String, String> map : data) {

            String file = map.get("file");
            if (file != null) {
                File f = new File(file);
                if (!f.exists()) continue;

                long len = f.length();
                if (len == 0) {
                    f.delete();
                    continue;
                }

                String filename = map.get("filename");
                String ext = "";
                int p = filename.lastIndexOf(".");
                if (p > 0 && p < filename.length() - 1) ext = filename.substring(p);
                Map<String, Object> uploadFile = new HashMap<>();
                String contentType = map.get("contentType");
                String name = map.get("name");
                uploadFile.put("filename", filename);
                uploadFile.put("contentType", contentType);
                uploadFile.put("file", file);
                uploadFile.put("size", len);
                uploadFile.put("ext", ext);
                uploadFile.put("name", name);
                files.add(uploadFile);
            } else {
                String name = map.get("name");
                String value = map.get("value");
                if (value != null && !value.isEmpty()) {

                    Object o = results.get(name);
                    if (o == null) {
                        results.put(name, value);
                    } else {
                        if (o instanceof String) {
                            List list = new ArrayList();
                            list.add(o);
                            list.add(value);
                            results.put(name, list);
                        }
                        if (o instanceof List) {
                            ((List) o).add(value);
                        }
                    }
                }
            }
        }

        if (files.size() > 0) results.put("files", files);
        String json = Json.toJson(results);
        return json;
    }

    void reset() {
        tempFile = null;
        tempFileOutputStream = null;
        tempUploaded = 0;
        tempMessage = null;
        lastContent.clear();
        boundary = null;
        data.clear();
    }


    void closeTempFile() {
        try {
            if (tempFileOutputStream != null)
                tempFileOutputStream.close();
        } catch (Throwable e) {
        }
    }

    boolean readNextPart() throws Exception {
        int saved = lastContent.readerIndex();
        Map<String, String> m = parsePartAttrs();
        if (m == null) {
            lastContent.readerIndex(saved);
            return false;
        }

        if (m.containsKey("filename")) {

            if( !new File(uploadDir).exists() ) {
                new File(uploadDir).mkdirs();
            }

            tempFile = uploadDir + "/" + uuid() + ".tmp";
            tempFileOutputStream = new BufferedOutputStream(new FileOutputStream(tempFile), 5000000);

            m.put("file", tempFile);
            data.add(m);
            return readMultiPartFile();
        } else {
            String v = readMultiPartValue();
            if (v == null) {
                lastContent.readerIndex(saved);
                return false;
            }
            m.put("value", v);
            data.add(m);
            return true;
        }
    }

    boolean readMultiPartFile() throws Exception {
        while (true) {
            int i = findCr();
            if (i == -1) {
                int len = lastContent.readableBytes();
                lastContent.readBytes(tempFileOutputStream, len);
                lastContent.clear();
                return false;
            }

            BoundaryResult ret = compareBoundary(i);
            int len = i;
            if (len > 0)
                lastContent.readBytes(tempFileOutputStream, len);

            if (ret.matched) {
                closeTempFile();
                tempFile = null;
                tempFileOutputStream = null;
                lastContent.readerIndex(ret.nextp);
                return true;
            } else {
                if (ret.nextp == lastContent.writerIndex()) { // partial match, need next chunk to compare boundary
                    return false;
                } else { // ignore current cr, continue findcr
                    lastContent.readBytes(tempFileOutputStream, 1);
                }
            }
        }
    }

    String readMultiPartValue() {
        int p1 = lastContent.readerIndex();
        int p2 = lastContent.writerIndex();
        int i = p1;
        while (i < p2) {
            byte b = lastContent.getByte(i);
            if (b == CR) {
                BoundaryResult ret = compareBoundary(i - p1);
                if (ret.matched) {
                    byte[] bs = new byte[i - p1];
                    lastContent.readBytes(bs);
                    String v = getString(bs, WebConstants.DefaultCharSet);
                    lastContent.readerIndex(ret.nextp);
                    return v;
                }
            }
            i += 1;
        }

        return null;
    }

    static class BoundaryResult {
        boolean matched;
        int nextp;

        BoundaryResult(boolean matched, int nextp) {
            this.matched = matched;
            this.nextp = nextp;
        }
    }

    BoundaryResult compareBoundary(int si) {
        int p1 = lastContent.readerIndex();
        int p2 = lastContent.writerIndex();
        int i = p1 + si;

        // match \n
        i += 1;
        if (i >= p2) return new BoundaryResult(false, i);
        byte b = lastContent.getByte(i);
        if (b != NL) {
            return new BoundaryResult(false, i);
        }

        // match boundary
        i += 1;
        if (i >= p2) return new BoundaryResult(false, i);

        int j = 0;
        while (j < boundary.length && i < p2) {
            b = lastContent.getByte(i);
            if (b != boundary[j]) return new BoundaryResult(false, i);
            j += 1;
            i += 1;
        }

        if (i >= p2) return new BoundaryResult(true, i);
        if (i + 1 >= p2) return new BoundaryResult(true, i);

        byte b1 = lastContent.getByte(i);
        byte b2 = lastContent.getByte(i + 1);

        if ((b1 == '-' && b2 == '-') || (b1 == CR && b2 == NL)) {
            i += 2;
        }
        return new BoundaryResult(true, i);
    }


    Map<String, String> parsePartAttrs() {

        String str = parsePart();
        if (str == null) return null;
        Map<String, String> map = new HashMap<>();
        String[] lines = str.split("\r\n");
        for (String line : lines) {
            int p = line.indexOf(":");
            if (p > 0) {
                String key = line.substring(0, p);
                switch (key) {
                    case "Content-Disposition": {

                        String s = line.substring(p + 1).trim();
                        String[] ss = s.split(";");
                        for (String ts : ss) {
                            String[] tss = ts.trim().split("=");
                            switch (tss[0]) {
                                case "name": {
                                    String v = tss[1].replace("\"", "");
                                    map.put("name", v);
                                    break;
                                }
                                case "filename": {
                                    String v = tss[1].replace("\"", "");
                                    int p1 = v.lastIndexOf("/");
                                    if (p1 >= 0) v = v.substring(p1 + 1);
                                    int p2 = v.lastIndexOf("\\");
                                    if (p2 >= 0) v = v.substring(p2 + 1);
                                    map.put("filename", v);
                                    break;
                                }
                                default:
                                    break;
                            }
                        }

                        break;
                    }

                    case "Content-Type": {
                        String contentType = line.substring(p + 1).trim();
                        map.put("contentType", contentType);
                        break;
                    }
                    default:
                        break;
                }
            }
        }
        return map;
    }

    String parsePart() {
        int p1 = lastContent.readerIndex();
        int p2 = lastContent.writerIndex();
        int i = p1;
        while (i < p2) {
            byte b = lastContent.getByte(i);
            if (b == '\n') {
                byte[] bs = new byte[i - p1 + 1];
                lastContent.getBytes(p1, bs);
                String line = getString(bs, WebConstants.DefaultCharSet);
                if (line.endsWith("\r\n\r\n")) {
                    lastContent.readerIndex(i + 1);
                    return line;
                }
            }
            i += 1;
        }
        return null;
    }

    String getString(byte[] bs, String charset) {
        try {
            return new String(bs, charset);
        } catch (Exception e) {
            return new String(bs);
        }
    }

    byte[] parseBoundary() {
        int i = findNl();
        if (i == -1) return null;
        byte[] bs = new byte[i + 1];
        int p1 = lastContent.readerIndex();
        lastContent.getBytes(p1, bs);

        String s = new String(bs).trim();
        byte[] bs2 = s.getBytes();
        lastContent.readerIndex(p1 + i + 1);
        return bs2;
    }

    int findCr() {
        int p1 = lastContent.readerIndex();
        int p2 = lastContent.writerIndex();
        int i = lastContent.indexOf(p1, p2, CR);
        if (i == -1) return -1;
        return i - p1;
    }

    int findNl() {
        int p1 = lastContent.readerIndex();
        int p2 = lastContent.writerIndex();
        int i = lastContent.indexOf(p1, p2, NL);
        if (i == -1) return -1;
        return i - p1;
    }

    String uuid() {
        return java.util.UUID.randomUUID().toString().replaceAll("-", "");
    }
}
