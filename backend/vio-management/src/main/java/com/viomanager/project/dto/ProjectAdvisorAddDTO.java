package com.viomanager.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 添加指导教师（PR09）：memberId 必填，目标必须是教师类型人员（7006）；
 * title 职称可选（展示用，不校验真实性）。
 */
@Data
public class ProjectAdvisorAddDTO {

    /** 教师人员档案 ID */
    @NotNull(message = "memberId 不能为空")
    private Long memberId;

    /** 职称（如 教授/副教授），可选 */
    @Size(max = 100, message = "职称最长 100 字")
    private String title;
}
