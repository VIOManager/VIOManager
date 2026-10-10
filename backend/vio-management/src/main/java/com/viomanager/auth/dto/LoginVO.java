package com.viomanager.auth.dto;

import lombok.Data;

/**
 * 登录成功返回的数据。
 */
@Data
public class LoginVO {

    /**
     * JWT，前端后续请求放 Authorization: Bearer <token>
     */
    private String token;

    /**
     * 用户 ID
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
     * 所属组织 ID
     */
    private Long orgId;

    /**
     * 是否仍为初始密码，true 时前端弹安全提示引导改密（PRD §3.1）
     */
    private Boolean defaultPassword;
}
