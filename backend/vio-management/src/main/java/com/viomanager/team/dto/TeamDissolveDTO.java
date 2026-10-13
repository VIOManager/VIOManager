package com.viomanager.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 解散赛队参数（TE12 body）：解散原因。 */
@Data
public class TeamDissolveDTO {

    @NotBlank(message = "解散原因不能为空")
    @Size(max = 500, message = "解散原因最长 500 字")
    private String reason;
}
