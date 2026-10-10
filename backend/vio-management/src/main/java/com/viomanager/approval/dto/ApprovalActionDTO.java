package com.viomanager.approval.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批处理动作参数（P02–P06 的 body）：comment 可空。
 */
@Data
public class ApprovalActionDTO {

    /** 处理意见 */
    @Size(max = 1000, message = "意见最长 1000 字")
    private String comment;
}
