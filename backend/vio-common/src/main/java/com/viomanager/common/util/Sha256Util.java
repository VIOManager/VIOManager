package com.viomanager.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 十六进制摘要：幂等键的请求体哈希（64 字符对齐 request_hash 列宽）、
 * 文件服务的内容指纹（对齐 file_object.sha256 列宽）共用一套实现。
 */
public final class Sha256Util {

    private Sha256Util() {
    }

    /** 对字符串取 SHA-256，返回 64 位小写十六进制 */
    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 对字节数组取 SHA-256，返回 64 位小写十六进制 */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < result.length; i++) {
                String hex = Integer.toHexString(result[i] & 0xFF);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256，理论到不了这里
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }
}
