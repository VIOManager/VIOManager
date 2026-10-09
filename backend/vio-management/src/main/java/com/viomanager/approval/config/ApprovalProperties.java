package com.viomanager.approval.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审批配置项（application.yml 的 vio.approval 段）。
 * 组长规矩：配置类一律放 config 包（模块内的 approval.config）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vio.approval")
public class ApprovalProperties {

    /**
     * 审批单有效期（天），默认 7：方案 §7 待确认项的建议值。
     * 到期未处理由定时任务转 EXPIRED。
     */
    private int expireDays = 7;
}
