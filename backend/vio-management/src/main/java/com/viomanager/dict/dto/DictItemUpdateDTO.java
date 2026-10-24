package com.viomanager.dict.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SY07 编辑字典条目（显示名/排序）。
 */
@Data
public class DictItemUpdateDTO {

    @NotBlank(message = "显示名不能为空")
    private String itemLabel;

    /** 排序号（选填，不传不动） */
    private Integer sortNo;
}
