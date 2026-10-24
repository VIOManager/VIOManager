package com.viomanager.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * SY02 新建字典类型。
 */
@Data
public class DictTypeCreateDTO {

    /** 字典编码：大写蛇形，创建后不可改 */
    @NotBlank(message = "字典编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "字典编码须大写蛇形（如 PROJECT_TAG）")
    private String dictCode;

    @NotBlank(message = "字典名称不能为空")
    private String dictName;

    /** 说明（选填） */
    private String remark;
}
