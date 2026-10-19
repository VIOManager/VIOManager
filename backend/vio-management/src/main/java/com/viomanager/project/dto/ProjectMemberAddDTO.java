package com.viomanager.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加项目成员（PR07）：body 仅 memberId——姓名/组织从 A 线 member 档案冗余，
 * 项目角色固定 MEMBER（OWNER 行只在建项目时由后端生成）。
 */
@Data
public class ProjectMemberAddDTO {

    /** 人员档案 ID */
    @NotNull(message = "memberId 不能为空")
    private Long memberId;
}
