package com.viomanager.auth.dto;

import lombok.Data;

/**
 * 本人档案（论坛独立库方案 §二.3）：登录态下查询，orgName 由 orgId 查 sys_org 得到。
 * 消费方是论坛服务——forum_user 快照首建/orgId 变化时转发用户 JWT 调此接口换组织名。
 */
@Data
public class MyProfileVO {

    /**
     * 用户 ID（sys_user.id，JWT userId 同源）
     */
    private Long userId;

    /**
     * 姓名
     */
    private String realName;

    /**
     * 角色：SUPER_ADMIN / DEPT_ADMIN / STUDENT
     */
    private String role;

    /**
     * 所属组织 ID（可能为空：超管不挂组织）
     */
    private Long orgId;

    /**
     * 所属组织名（orgId 为空或组织已删时为 null，消费方按空处理）
     */
    private String orgName;
}
