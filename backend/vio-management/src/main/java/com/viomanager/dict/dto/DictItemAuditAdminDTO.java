package com.viomanager.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * SY10 审核用户补充词条（PUT 形态，系统配置页用；与 D03 的 POST {approve/pass} 并存）。
 * comment 只进审计摘要不落条目表（sys_dict_item 无该列）。
 */
@Data
public class DictItemAuditAdminDTO {

    /** 审核结论 */
    @NotBlank(message = "审核结论不能为空")
    @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "审核结论仅支持 APPROVED / REJECTED")
    private String auditStatus;

    /** 审核备注（选填，进审计日志） */
    private String comment;
}
