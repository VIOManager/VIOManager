package com.viomanager.material.support;

import com.viomanager.common.auth.CurrentUser;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户 → 人员档案 ID（sys_user.member_id）反查支撑，
 * material / achievement 两包共用（原先在两个 ServiceImpl 里各抄一份私有方法）。
 * 未绑定档案（member_id 为 NULL，如 admin 账号）返回 null，拦截口径由调用方决定。
 * 另提供「人员 ID → 姓名」批量查询，供导出等需要展示归属人的场景免 N+1。
 */
// TODO(联调) 骨架 ApprovalActionServiceImpl 有同款逻辑，联调时可上提合并到认证域
@Component
public class MemberIdSupport {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public Long currentMemberId(CurrentUser user) {
        //1.空防线：未登录或无 userId 直接返回，调用方按业务口径拦截
        if (user == null || user.getUserId() == null) {
            return null;
        }
        //2.反查人员档案 ID：sys_user 一人一行，member_id 可空（admin 账号未绑定档案）
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT member_id FROM sys_user WHERE id = ? AND deleted = 0",
                Long.class, user.getUserId());
        if (ids.isEmpty() || ids.get(0) == null) {
            return null;
        }
        return ids.get(0);
    }

    /**
     * 批量查人员姓名（memberId → name），供导出等展示场景免 N+1；空集直接返回空 Map。
     * 查不到的 ID 不会出现在返回 Map 里，调用方自行兜底（如回退业务表冗余列）。
     */
    public Map<Long, String> loadMemberNames(Collection<Long> memberIds) {
        Map<Long, String> names = new HashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return names;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < memberIds.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        jdbcTemplate.query(
                "SELECT id, name FROM member WHERE deleted = 0 AND id IN (" + placeholders + ")",
                rs -> {
                    names.put(rs.getLong("id"), rs.getString("name"));
                },
                memberIds.toArray());
        return names;
    }
}
