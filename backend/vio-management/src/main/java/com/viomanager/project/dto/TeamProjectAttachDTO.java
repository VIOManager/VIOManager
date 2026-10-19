package com.viomanager.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 赛队挂载项目（PR13）：body 含 projectId 必填 + trackId 可选。
 * contestId 不由前端传——冗余自赛队所属比赛（一队一比赛）。
 */
@Data
public class TeamProjectAttachDTO {

    /** 项目 ID */
    @NotNull(message = "projectId 不能为空")
    private Long projectId;

    /** 挂载时选择的赛道 ID（可选，须属于该比赛且启用） */
    private Long trackId;
}
