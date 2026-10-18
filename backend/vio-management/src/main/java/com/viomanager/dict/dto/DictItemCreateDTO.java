package com.viomanager.dict.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * D02 用户补充技能字典项入参。
 * 提交后 audit_status=PENDING，管理员 D03 审核通过才入下拉。
 */
@Data
public class DictItemCreateDTO {

    /** 字典值（存库编码，如 unity） */
    @NotBlank(message = "技能值不能为空")
    private String itemValue;

    /** 展示文案（下拉框文字，如 Unity 开发） */
    @NotBlank(message = "技能名称不能为空")
    private String itemLabel;
}
