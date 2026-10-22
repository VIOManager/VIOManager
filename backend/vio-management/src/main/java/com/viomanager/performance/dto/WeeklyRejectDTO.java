package com.viomanager.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PF08 驳回入参（reason 必填）。
 */
@Data
public class WeeklyRejectDTO {

    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 最长 500 字")
    private String reason;
}
