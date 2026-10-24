package com.viomanager.sysconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * CF02 修改系统参数值。
 */
@Data
public class ConfigUpdateDTO {

    @NotBlank(message = "参数值不能为空")
    private String configValue;
}
