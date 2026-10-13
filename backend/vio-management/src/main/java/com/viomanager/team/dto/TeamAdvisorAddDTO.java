package com.viomanager.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 加指导教师参数（TE09 body）：教师 member_id（member_type=TEACHER）。 */
@Data
public class TeamAdvisorAddDTO {

    @NotNull(message = "memberId 不能为空")
    private Long memberId;
}
