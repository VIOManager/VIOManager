package com.viomanager.material.support;

import com.viomanager.common.auth.CurrentUser;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户 → 人员档案 ID（sys_user.member_id）反查支撑，
 * material / achievement 两包共用（原先在两个 ServiceImpl 里各抄一份私有方法）。
 * 未绑定档案（member_id 为 NULL，如 admin 账号）返回 null，拦截口径由调用方决定。
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
}
