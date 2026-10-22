package com.viomanager.common.audit;

import com.viomanager.common.config.AuditProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 审计日志落库（audit_log 表）。
 * 该表没有 deleted / updated_at 基座字段（日志只增不改），刻意不走 MyBatis-Plus 的
 * BaseEntity 那套，直接 JdbcTemplate 裸 SQL。
 * 表名可跨库限定（2026-10-18 拍板：论坛服务配 viomanager.audit_log 直写管理库，
 * 同实例前提；主站不配默认写自己的库，行为不变）。
 */
@Component
public class AuditLogDao {

    /** 表名合法格式：裸表名或 库名.表名（配置信任源是 yml，校验防手误写坏 SQL） */
    private static final String TABLE_PATTERN = "[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?";

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private AuditProperties auditProperties;

    /** 启动即校验表名：非法直接拒绝启动，比写库时在后台线程里反复失败好排查 */
    @PostConstruct
    public void checkTableName() {
        String table = auditProperties.getTableName();
        if (table == null || !table.matches(TABLE_PATTERN)) {
            throw new IllegalStateException("vio.audit.table-name 不是合法表名: " + table);
        }
    }

    /** 插一条审计记录（列与表一一对应，超长截断由切面负责） */
    public void insert(AuditRecord record) {
        String sql = "INSERT INTO " + auditProperties.getTableName()
                + " (id, user_id, login_name, real_name, org_id, role, "
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
