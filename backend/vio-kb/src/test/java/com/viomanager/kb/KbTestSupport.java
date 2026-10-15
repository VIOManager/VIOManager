package com.viomanager.kb;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 安全网测试夹具：直插测试数据、读计数列、按前缀清理。
 * 独立成类（而非塞在测试里）是为了守住单文件 300 行铁律——拆分封装，不是凑行数。
 */
@Component
public class KbTestSupport {

    /** 测试数据统一前缀：@AfterEach 按它清理三张表 */
    static final String PREFIX = "安全网";

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 直插一篇测试文档（三计数清零，便于断言增量），返回 id */
    public long insertDoc(String title, String status, long orgId) {
        long id = testId();
        jdbcTemplate.update(
                "INSERT INTO kb_doc (id, title, category, status, org_id, view_count, collect_count, comment_count) "
                        + "VALUES (?, ?, '其他', ?, ?, 0, 0, 0)",
                id, title, status, orgId);
        return id;
    }

    /** 直插一条可见评论，并同步把 comment_count +1（模拟正常发评后的存量状态），返回 id */
    public long insertComment(long docId, long memberId, String content) {
        long id = testId();
        jdbcTemplate.update(
                "INSERT INTO kb_comment (id, doc_id, content, member_id, member_name, status) "
                        + "VALUES (?, ?, ?, ?, '测试人', 'VISIBLE')",
                id, docId, content, memberId);
        jdbcTemplate.update("UPDATE kb_doc SET comment_count = comment_count + 1 WHERE id = ?", docId);
        return id;
    }

    /** 读计数列（kb_doc 按主键查，其余表按 doc_id 查） */
    public int count(String table, String column, long docId) {
        String keyColumn = "kb_doc".equals(table) ? "id" : "doc_id";
        Integer value = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM " + table + " WHERE " + keyColumn + " = ?",
                Integer.class, docId);
        return value == null ? 0 : value;
    }

    /** 清理本前缀测试数据（三张表级联按 doc_id）与测试幂等键 */
    public void cleanup() {
        List<Long> docIds = jdbcTemplate.queryForList(
                "SELECT id FROM kb_doc WHERE title LIKE '" + PREFIX + "%'", Long.class);
        if (!docIds.isEmpty()) {
            String in = docIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbcTemplate.update("DELETE FROM kb_comment WHERE doc_id IN (" + in + ")");
            jdbcTemplate.update("DELETE FROM kb_collect WHERE doc_id IN (" + in + ")");
            jdbcTemplate.update("DELETE FROM kb_doc WHERE id IN (" + in + ")");
        }
        jdbcTemplate.update("DELETE FROM idempotent_record WHERE idem_key LIKE 'test-kb-%'");
    }

    /** 测试用唯一 ID：99 开头的小数值段，与雪花 ID（19 位）不可能撞车 */
    private long testId() {
        return Long.parseLong(String.format("99%04d", System.nanoTime() % 10000)
                + (System.currentTimeMillis() % 100000));
    }
}
