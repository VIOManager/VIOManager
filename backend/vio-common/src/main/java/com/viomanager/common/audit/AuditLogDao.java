package com.viomanager.common.audit;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 审计日志落库（audit_log 表）。
 * 该表没有 deleted / updated_at 基座字段（日志只增不改），刻意不走 MyBatis-Plus 的
 * BaseEntity 那套，直接 JdbcTemplate 裸 SQL——论坛服务（阶段2）引同一个 common jar 即用。
 */
@Component
public class AuditLogDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 插一条审计记录（列与表一一对应，超长截断由切面负责） */
    public void insert(AuditRecord record) {
        String sql = "INSERT INTO audit_log (id, user_id, login_name, real_name, org_id, role, "
                + "ip, user_agent, http_method, uri, biz_type, biz_type_label, summary, "
                + "request_params, success, error_message, cost_millis, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, record.getId(), record.getUserId(), record.getLoginName(),
                record.getRealName(), record.getOrgId(), record.getRole(), record.getIp(),
                record.getUserAgent(), record.getHttpMethod(), record.getUri(), record.getBizType(),
                record.getBizTypeLabel(), record.getSummary(), record.getRequestParams(),
                record.getSuccess(), record.getErrorMessage(), record.getCostMillis(),
                LocalDateTime.now());
    }
}
