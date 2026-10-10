package com.viomanager.approval.vo;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批详情出参（P09）：列表项全部字段 + 申请内容快照 + 操作流水。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalDetailVO extends ApprovalVO {

    /** 申请内容快照 JSON（前端按业务类型解析展示） */
    private String bizPayload;

    /** 操作流水（按时间正序） */
    private List<ApprovalRecordVO> records;
}
