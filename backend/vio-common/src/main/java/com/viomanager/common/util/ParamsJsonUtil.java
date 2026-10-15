package com.viomanager.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import org.springframework.web.multipart.MultipartFile;

/**
 * 方法入参转 JSON 摘要：幂等切面的请求体哈希、审计切面的 request_params 共用一套规则。
 * 规则：先脱敏再 JSON；参数里混了不可序列化对象（文件流、原生请求对象）时
 * 退化为 deepToString——Lombok 的 toString 同样稳定，重试时哈希不会漂。
 * ⚠️ Servlet 原生对象/流必须先脱敏成类名占位符：Jackson 按 bean 规约序列化 response 时
 * 会把 getWriter()/getOutputStream() 当属性 getter 调用，getWriter 一旦被调，业务再
 * getOutputStream 就抛 already been called——下载/导出接口随机 500（2026-10-15 实证竞态复现）。
 */
public final class ParamsJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ParamsJsonUtil() {
    }

    /** Servlet 原生对象/流不能进 JSON：getter 有副作用会污染响应流，占位符替代 */
    private static Object desensitize(Object arg) {
        if (arg == null
                || arg instanceof ServletRequest || arg instanceof ServletResponse
                || arg instanceof MultipartFile
                || arg instanceof InputStream || arg instanceof OutputStream) {
            return arg == null ? null : arg.getClass().getSimpleName();
        }
        return arg;
    }

    /** 参数数组转 JSON（控制器方法入参的通用形态） */
    public static String toJson(Object[] args) {
        Object[] safe = Arrays.stream(args).map(ParamsJsonUtil::desensitize).toArray();
        try {
            return MAPPER.writeValueAsString(safe);
        } catch (Exception e) {
            return Arrays.deepToString(args);
        }
    }

    /** 任意对象转 JSON；失败退化为 toString（摘要用途，不苛求精确） */
    public static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
