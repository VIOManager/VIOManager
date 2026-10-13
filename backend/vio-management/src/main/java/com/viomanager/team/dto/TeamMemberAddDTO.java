package com.viomanager.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 加人申请参数（TE04 body）：被邀人 member_id。 */
@Data
public class TeamMemberAddDTO {

    @NotNull(message = "memberId 不能为空")
    private Long memberId;
}
