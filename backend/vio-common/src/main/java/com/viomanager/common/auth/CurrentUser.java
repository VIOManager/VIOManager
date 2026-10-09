package com.viomanager.common.auth;

import lombok.Data;

/**
 * 当前登录用户身份，来自 JWT 载荷，由 JwtAuthFilter 解析后放入 UserHolder。
 * ⚠️ 安全：这里的字段只能来自 token 验签结果，任何请求参数都不可写入——
 * 否则前端伪造 orgId/role 就能横向提权（规范 §5.2：越权由后端封堵）。
 */
@Data
public class CurrentUser {

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 登录名（学工号）。Step 6 审计要记"谁"的操作，比 userId 直观，随 token 冗余下发 */
    private String loginName;

    /** 角色：SUPER_ADMIN / DEPT_ADMIN / STUDENT */
    private String role;

    /** 所属组织 ID（部门管理员的数据范围根节点），可为空 */
    private Long orgId;

    /** 姓名冗余（论坛阶段2 展示发帖人用，不回查人员档案） */
    private String realName;

    /** 密码最后修改时间（毫秒时间戳，0 = 从未改过）。token 里带的旧值与库里的新值不一致即判定 token 失效 */
    private Long pwdUpdatedAt;
}
