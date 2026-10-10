package com.viomanager.file.service;

import com.viomanager.file.config.FileProperties;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ClamAV 病毒扫描客户端：clamd 的 INSTREAM 协议（compose 的 clamav 容器，端口 3310）。
 * 协议很短：发 "zINSTREAM\0" 命令 → 按"4 字节大端长度 + 数据"分块发文件内容 →
 * 发 4 字节 0 表示结束 → 读应答，"stream: OK" 干净，"stream: 病毒名 FOUND" 命中。
 * 刻意手写 Socket 不引第三方库：协议就这一页纸，引库反而多个依赖要维护。
 */
@Slf4j
@Component
public class ClamAvScanner {

    /** 单块大小 32KB：clamd 默认上限是 2MB，块太大直接被断连 */
    private static final int CHUNK_SIZE = 32 * 1024;

    private static final int CONNECT_TIMEOUT_MILLIS = 3000;

    private static final int READ_TIMEOUT_MILLIS = 20000;

    @Resource
    private FileProperties fileProperties;

    /** 扫描结果：clean=true 干净；命中病毒时 signature 是特征名（如 Eicar-Signature） */
    @Data
    public static class ScanResult {
        private boolean clean;
        private String signature;
    }

    /**
     * 扫描文件字节。抛 IOException = 扫描服务不可用（连不上 / 超时），
     * 调用方据此转 1011；返回结果只有干净或命中两种，不会静默失败。
     */
    public ScanResult scan(byte[] bytes) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    fileProperties.getClamav().getHost(), fileProperties.getClamav().getPort()),
                    CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            //1.发 INSTREAM 命令（z 前缀 + \0 结尾）
            out.write("zINSTREAM".getBytes(StandardCharsets.US_ASCII));
            out.write(0);
            //2.分块发内容：每块前带 4 字节大端长度
            int offset = 0;
            while (offset < bytes.length) {
                int chunkSize = Math.min(CHUNK_SIZE, bytes.length - offset);
                out.write(intToBigEndian(chunkSize));
                out.write(bytes, offset, chunkSize);
                offset += chunkSize;
            }
            //3.长度为 0 的块表示传输结束
            out.write(new byte[4]);
            out.flush();
            //4.读应答并判定
            String reply = readReply(in);
            if ("stream: OK".equals(reply)) {
                ScanResult result = new ScanResult();
                result.setClean(true);
                return result;
            }
            if (reply != null && reply.endsWith("FOUND")) {
                ScanResult result = new ScanResult();
                result.setClean(false);
                result.setSignature(reply.replace("stream: ", "").replace(" FOUND", ""));
                return result;
            }
            // 应答格式都不认识，按扫描服务异常处理，绝不猜"应该是干净的"
            throw new IOException("ClamAV 应答无法识别：" + reply);
        }
    }

    /** 按行读应答（读到 \n 或连接关闭为止） */
    private String readReply(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        int b = in.read();
        while (b != -1 && b != '\n') {
            builder.append((char) b);
            b = in.read();
        }
        return builder.toString().trim();
    }

    /** int 转 4 字节大端（协议要求的块长度前缀） */
    private byte[] intToBigEndian(int value) {
        return new byte[]{
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }
}
