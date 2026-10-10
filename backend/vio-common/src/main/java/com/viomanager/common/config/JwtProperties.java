package com.viomanager.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项（application.yml 的 vio.jwt 段）。
 * 组长规矩：配置类一律放 config 包。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vio.jwt")
public class JwtProperties {

    /**
     * HS256 签名密钥，至少 32 字节。
     * ⚠️ 安全：生产必须用环境变量 JWT_SECRET 覆盖开发默认值（PRD §5：密钥必填不用公开默认值）
     */
    private String secret;

    /**
     * token 时效（小时），默认 2：阶段2 论坛只验签不查库，改密踢人靠短时效兜底（架构调整方案 §4）
     */
    private long expireHours = 2;
}
