package com.viomanager.schedule.support;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 组织读取支撑（A 线 sys_org 表，JdbcTemplate 直读）。
 * 职责：子组织合法性校验（排班/绩效的隔离维度必须是 is_sub_org=1 的节点）、
 * 批量取组织名（VO 冗余展示）、按当前角色算可见组织集合（管理端列表过滤）。
 */
@Component
public class SubOrgSupport {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 组织只读行（显式列名）。 */
    public record OrgRow(Long id, String name, Long parentId, Integer isSubOrg, String path) {
    }

    /** 校验目标组织存在且为分子组织（排班/绩效隔离维度），否则 1004。 */
    public OrgRow requireSubOrg(Long orgId) {
        OrgRow row = requireOrg(orgId);
        if (row.isSubOrg() == null || row.isSubOrg() != 1) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "目标组织不是分子组织，排班与绩效按分子组织隔离");
        }
        return row;
    }

    /** 按 id 读组织（不限类型），不存在/已删抛 1004。 */
    public OrgRow requireOrg(Long orgId) {
        List<OrgRow> rows = jdbcTemplate.query(
                "SELECT id, name, parent_id, is_sub_org, path FROM sys_org WHERE id = ? AND deleted = 0",
                (rs, i) -> new OrgRow(rs.getLong(1), rs.getString(2),
                        rs.getObject(3) == null ? null : rs.getLong(3),
                        rs.getInt(4), rs.getString(5)),
                orgId);
        if (rows.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "组织不存在");
        }
        return rows.get(0);
    }

    /** 批量取组织名（MySchedule/周报列表的 subOrgName 冗余），缺的组织返回原名占位不报错。 */
    public Map<Long, String> orgNames(Collection<Long> ids) {
        Map<Long, String> map = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        jdbcTemplate.query(
                "SELECT id, name FROM sys_org WHERE deleted = 0 AND id IN (" + placeholders + ")",
                rs -> {
                    map.put(rs.getLong(1), rs.getString(2));
                },
                ids.toArray());
        return map;
    }

    /**
     * 当前用户可见的组织 ID 集合（管理端列表过滤用）：
     * 超管返回 null（= 不过滤全量）；部门管理员返回本组织子树（含自身）；
     * 学生到这里就是漏了角色门槛，直接拒绝（fail-closed）。
     */
    public List<Long> visibleOrgIds(CurrentUser user) {
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return null;
        }
        if (!"DEPT_ADMIN".equals(user.getRole())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "该接口仅管理员可用");
        }
        if (user.getOrgId() == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "部门管理员未配置所属组织");
        }
        OrgRow root = requireOrg(user.getOrgId());
        List<Long> ids = new ArrayList<>();
        if (root.path() == null || root.path().isEmpty()) {
            ids.add(root.id());
            return ids;
        }
        //path 形如 /0/1/12/ 且以斜杠结尾：LIKE 'path%' 匹配自身与子孙，不会误配 /0/1/120/
        jdbcTemplate.query(
                "SELECT id FROM sys_org WHERE path LIKE CONCAT(?, '%') AND deleted = 0",
                rs -> {
                    ids.add(rs.getLong(1));
                },
                root.path());
        if (ids.isEmpty()) {
            ids.add(root.id());
        }
        return ids;
    }
}
