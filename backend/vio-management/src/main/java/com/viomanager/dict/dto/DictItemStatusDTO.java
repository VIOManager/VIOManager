package com.viomanager.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * SY09 启用/停用字典条目。
 */
@Data
public class DictItemStatusDTO {

    /** 目标状态：ENABLED 启用 / DISABLED 停用 */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态仅支持 ENABLED / DISABLED")
    private String status;
}
