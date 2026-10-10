package com.viomanager.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 幂等配置项（application.yml 的 vio.idempotent 段）。
 * 组长规矩：配置类一律放 config 包。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vio.idempotent")
public class IdempotentProperties {

    /**
     * 幂等键有效期（小时），默认 24：规范 §9 待确认项的建议值。
     * 过期后同 key 视为新请求，可复用（idempotent_record.expire_at 控制）。
     */
    private long expireHours = 24;
}
