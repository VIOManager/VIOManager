package com.viomanager.person.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * M13 账号入参：POST 绑定与 PUT 修改共用。
 * POST：loginName 可空，默认取学工号、再退学号；初始密码 abc123 由账号服务兜底。
 * PUT：loginName 非空且与当前不同则改登录名；resetPassword=true 则重置回初始密码并踢掉旧 token。
 */
@Data
public class PersonAccountDTO {

    /** 登录名（学工号风格；绑定时可空走默认规则） */
    @Size(max = 32, message = "登录名最长 32 字")
    private String loginName;

    /** 仅 PUT 生效：true = 重置密码回初始密码 abc123，旧 token 全部失效 */
    private Boolean resetPassword;
}
