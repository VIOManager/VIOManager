package com.viomanager.semester.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * S05 发起切换入参：目标学期 + 超管密码二次确认（PRD §3.4）。
 * 密码比对当前登录超管自己的 sys_user.password，错则 1001 不区分细节。
 */
@Data
public class SwitchTaskCreateDTO {

    /** 目标学期 ID */
    @NotNull(message = "目标学期不能为空")
    private Long toSemesterId;

    /** 当前登录超管的密码（二次确认） */
    @NotBlank(message = "请输入密码二次确认")
    private String password;
}
