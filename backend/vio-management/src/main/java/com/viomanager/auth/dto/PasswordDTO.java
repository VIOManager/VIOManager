package com.viomanager.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改本人密码的请求参数。
 */
@Data
public class PasswordDTO {

    /**
     * 原密码
     */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /**
     * 新密码（初始密码 abc123 也是 6 位，新密码不短于它）
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码至少 6 位")
    private String newPassword;
}
