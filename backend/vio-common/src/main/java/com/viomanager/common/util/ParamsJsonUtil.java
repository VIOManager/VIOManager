package com.viomanager.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;

/**
 * 方法入参转 JSON 摘要：幂等切面的请求体哈希、审计切面的 request_params 共用一套规则。
 * 规则：能 JSON 就 JSON；参数里混了不可序列化对象（文件流、原生请求对象）时
 * 退化为 deepToString——Lombok 的 toString 同样稳定，重试时哈希不会漂。
 */
public final class ParamsJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ParamsJsonUtil() {
    }

    /** 参数数组转 JSON（控制器方法入参的通用形态） */
    public static String toJson(Object[] args) {
        try {
            return MAPPER.writeValueAsString(args);
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
