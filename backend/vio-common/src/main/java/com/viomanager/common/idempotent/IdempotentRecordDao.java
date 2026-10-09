package com.viomanager.common.idempotent;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等键存取（idempotent_record 表）。
 * 该表没有 deleted / updated_at 基座字段，刻意不走 MyBatis-Plus 的 BaseEntity 那套，
 * 直接 JdbcTemplate 裸 SQL——论坛服务（阶段2）引同一个 common jar 即用，
 * 各服务落各自的库，不需要各自配 mapper 扫描。
 */
@Component
public class IdempotentRecordDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 按 key 查记录，没有返回 null。
     * expired 由数据库算好（1=已过期 0=未过期），避免 Java 侧和数据库时区不一致的误判。
     */
    public Map<String, Object> findByKey(String idemKey) {
        String sql = "SELECT id, request_hash, IF(expire_at < NOW(), 1, 0) AS expired "
                + "FROM idempotent_record WHERE idem_key = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, idemKey);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    /**
     * 插占位记录：业务执行前先占坑（result_summary 留空），
     * 靠唯一键 uk_idem_key 保证并发下只有一个请求能占到。
     */
    public void insertRecord(long id, String idemKey, String bizType, Long userId,
                             String requestHash, LocalDateTime expireAt) {
        String sql = "INSERT INTO idempotent_record "
                + "(id, idem_key, biz_type, user_id, request_hash, result_summary, expire_at, created_at) "
                + "VALUES (?, ?, ?, ?, ?, NULL, ?, ?)";
        jdbcTemplate.update(sql, id, idemKey, bizType, userId, requestHash, expireAt, LocalDateTime.now());
    }

    /** 业务成功后回填首次执行结果摘要（超长截断由调用方负责，列宽 500） */
    public void updateSummary(String idemKey, String summary) {
        String sql = "UPDATE idempotent_record SET result_summary = ? WHERE idem_key = ?";
        jdbcTemplate.update(sql, summary, idemKey);
    }

    /** 释放 key：业务失败时删占位放行重试；过期 key 的清理复用同一方法 */
    public void deleteByKey(String idemKey) {
        String sql = "DELETE FROM idempotent_record WHERE idem_key = ?";
        jdbcTemplate.update(sql, idemKey);
    }
}
