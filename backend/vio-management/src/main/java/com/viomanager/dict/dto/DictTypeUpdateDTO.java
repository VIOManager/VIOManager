package com.viomanager.dict.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SY03 编辑字典类型（仅名称/说明，dictCode 不可改）。
 */
@Data
public class DictTypeUpdateDTO {

    @NotBlank(message = "字典名称不能为空")
    private String dictName;

    /** 说明（选填） */
    private String remark;
}
