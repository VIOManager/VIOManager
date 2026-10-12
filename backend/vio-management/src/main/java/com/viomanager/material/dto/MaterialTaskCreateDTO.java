package com.viomanager.material.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 创建材料任务入参（MT02）。
 * deadline 入参格式 "yyyy-MM-dd HH:mm"，可空——数据库该列允许 NULL，空值视为不限期。
 * 同一比赛唯一任务的规则由 service 层先查后建拦截（material_task 无唯一索引兜底）。
 */
@Data
public class MaterialTaskCreateDTO {

    /** 所属比赛 ID（B 线 contests 表） */
    @NotNull(message = "contestId 不能为空")
    private Long contestId;

    /** 任务名称 */
    @NotBlank(message = "name 不能为空")
    private String name;

    /** 任务说明 */
    private String description;

    /** 截止时间，可空（为空视为不限期） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime deadline;
}
