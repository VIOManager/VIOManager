package com.viomanager.approval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审批列表查询参数（P01）：view status bizType page size。
 */
@Data
public class ApprovalQueryDTO {

    /** 视角：TODO 我的待办（管理员）/ MINE 我发起的 / TARGET 我是目标人 */
    @NotBlank(message = "view 不能为空")
    private String view;

    /** 状态筛选：PENDING / APPROVED / REJECTED / CANCELLED / EXPIRED，可空 */
    private String status;

    /** 业务类型筛选，可空 */
    private String bizType;

    /** 标题关键词，模糊匹配 biz_title，可空 */
    private String keyword;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "page 最小为 1")
    private long page = 1;

    /** 每页条数，默认 20 上限 100（规范 §2.3） */
    @Min(value = 1, message = "size 最小为 1")
    @Max(value = 100, message = "size 最大为 100")
    private long size = 20;
}
