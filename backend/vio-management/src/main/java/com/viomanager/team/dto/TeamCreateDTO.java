package com.viomanager.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 组队申请参数（TE03 body）。
 * prefillMemberIds 数量须 ≤ 生效规则 prefillLimit（3 人队=2、5 人队=2）。
 */
@Data
public class TeamCreateDTO {

    @NotNull(message = "contestId 不能为空")
    private Long contestId;

    /** 赛道 ID，无赛道的比赛可空 */
    private Long trackId;

    @NotBlank(message = "队名不能为空")
    @Size(max = 200, message = "队名最长 200 字")
    private String name;

    @Size(max = 200, message = "项目名最长 200 字")
    private String projectName;

    /** 报名时预填队员（不含队长），数量 ≤ prefillLimit */
    private List<Long> prefillMemberIds;
}
