package com.viomanager.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审计配置项（application.yml 的 vio.audit 段）。
 * 组长规矩：配置类一律放 config 包。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vio.audit")
public class AuditProperties {

    /**
     * 总开关，默认开：排查性能问题时可一键停审计，业务完全不受影响
     */
    private boolean enabled = true;

    /**
     * 是否异步写库，默认 true（接口不等写库完成）。
     * ⚠️ 测试与排查问题时设 false：同步写完立即可查（vio.audit.async=false）
     */
    private boolean async = true;
}
