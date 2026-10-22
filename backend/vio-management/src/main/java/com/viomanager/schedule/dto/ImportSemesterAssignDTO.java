package com.viomanager.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * TT05 归一化失败批次人工指定学期入参。仅 PENDING 且未关联学期的批次可用（8003），指定后立即续跑解析。
 */
@Data
public class ImportSemesterAssignDTO {

    @NotNull(message = "semesterId 不能为空")
    private Long semesterId;
}
