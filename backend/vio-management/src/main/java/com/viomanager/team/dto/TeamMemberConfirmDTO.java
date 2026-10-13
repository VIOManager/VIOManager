package com.viomanager.team.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 队员确认/拒绝参数（TE06 body）：action 取 CONFIRM 或 REJECT。 */
@Data
public class TeamMemberConfirmDTO {

    @NotBlank(message = "action 不能为空")
    private String action;
}
