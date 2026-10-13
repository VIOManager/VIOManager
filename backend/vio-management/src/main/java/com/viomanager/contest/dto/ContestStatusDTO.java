package com.viomanager.contest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 比赛状态流转参数（C06 body）。
 * to 须为状态机下一态：DRAFT→PUBLISHED→REGISTERING→RUNNING→FINISHED→ARCHIVED。
 * DRAFT→PUBLISHED 时触发发布前检查（4001 逐项指出缺项）。
 */
@Data
public class ContestStatusDTO {

    @NotBlank(message = "目标状态不能为空")
    private String to;
}
