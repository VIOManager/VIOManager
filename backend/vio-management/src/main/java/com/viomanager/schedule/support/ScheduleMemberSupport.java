package com.viomanager.schedule.support;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.material.support.MemberIdSupport;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 排班/绩效侧人员读取支撑（A 线 member 表，JdbcTemplate 直读）。
 * 当前用户 → memberId 反查复用 material 的 MemberIdSupport（achievement 同款跨包引用先例）；
 * 本类补按 id 批量读、按班级全量读（空课名单）与"当前人须绑定档案"的强校验。
 */
@Component
public class ScheduleMemberSupport {

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 人员只读行（显式列名）。studentNo 用于年级推导（STUDENT_NO 优先）。 */
    public record MemberBasic(Long id, String name, String className, String studentNo, Long orgId) {
    }

    /** 当前登录人的人员档案 ID；学生侧接口要求必须绑定，未绑定抛 1001（admin 账号无档案属配置错误）。 */
    public Long requireCurrentMemberId() {
        CurrentUser user = com.viomanager.common.auth.UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        if (memberId == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "当前账号未绑定人员档案，无法执行学生侧操作");
        }
        return memberId;
    }

    /** 按 id 读单人，不存在/已删抛 1004。 */
    public MemberBasic requireMember(Long memberId) {
        List<MemberBasic> rows = byIds(List.of(memberId));
        if (rows.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "人员不存在");
        }
        return rows.get(0);
    }

    /** 按 id 集合批量读（排班成员名册、绩效列表补姓名），返回 id → 行。 */
    public Map<Long, MemberBasic> membersByIds(Collection<Long> ids) {
        Map<Long, MemberBasic> map = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (MemberBasic row : byIds(ids)) {
            map.put(row.id(), row);
        }
        return map;
    }

    /** 全部在册且有班级的人员（空课名单候选；百人量级全量读内存过滤，避免 SQL 里做归一化函数）。 */
    public List<MemberBasic> allActiveWithClass() {
        return jdbcTemplate.query(
                "SELECT id, name, class_name, student_no, org_id FROM member "
                        + "WHERE deleted = 0 AND status = 1 AND class_name IS NOT NULL AND class_name <> ''",
                (rs, i) -> new MemberBasic(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getObject(5) == null ? null : rs.getLong(5)));
    }

    private List<MemberBasic> byIds(Collection<Long> ids) {
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        return jdbcTemplate.query(
                "SELECT id, name, class_name, student_no, org_id FROM member "
                        + "WHERE deleted = 0 AND id IN (" + placeholders + ")",
                (rs, i) -> new MemberBasic(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getObject(5) == null ? null : rs.getLong(5)),
                ids.toArray());
    }
}
