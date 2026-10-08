package com.viomanager.common.web;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局定制：Long → String 序列化。
 * 依据规范 §2.1：雪花 ID 是 19 位长整数，超过 JS Number 安全上限（2^53-1），
 * 前端拿到会精度丢失，因此所有 Long 出参一律按字符串传输。
 */
@Configuration
public class JacksonConfig {

    /**
     * ⚠️ 冲突：作用于所有 Long 字段（不区分是否 id）——前端统一按字符串处理数字型 ID。
     * 入参方向不受影响：Jackson 仍能把 "123" 反序列化回 Long。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        // builder -> {...} 是框架要求的功能接口写法（lambda），无法换成普通方法；
        // 意思是"拿到框架的 builder，往里登记两条序列化规则"
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
