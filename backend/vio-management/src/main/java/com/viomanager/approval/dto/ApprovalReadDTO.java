package com.viomanager.approval.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 已读标记参数（P07）：ids 数组 + asRole，只置当前视角的已读位。
 */
@Data
public class ApprovalReadDTO {

    /** 要标记的审批单 ID 列表 */
    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;

    /** 以什么视角标记：APPLICANT 发起人 / TARGET 目标人 / APPROVER 审批人 */
    @NotNull(message = "asRole 不能为空")
    private String asRole;
}
