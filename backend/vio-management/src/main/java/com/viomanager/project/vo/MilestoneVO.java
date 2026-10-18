package com.viomanager.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 时间节点列表项（PR16）：管理员看全部状态与审核信息；普通用户仅见 APPROVED 基础字段。
 */
@Data
public class MilestoneVO {

    /** 节点 ID */
    private Long id;

    /** 所属比赛 ID */
    private Long contestId;

    /** 节点类型：REGISTER_START / REGISTER_END / WORK_SUBMIT / OTHER */
    private String nodeType;

    /** 节点名称 */
    private String name;

    /** 节点时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nodeTime;

    /** 说明 */
    private String description;

    /** 状态：DRAFT / PENDING / APPROVED / REJECTED */
    private String status;

    /** 审核通过时间（仅管理员视图回显） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    /** 审批人姓名（仅管理端列表回填；学生视图只回 APPROVED 基础字段） */
    private String approvedBy;

    /** 驳回原因（仅管理员视图回显，REJECTED 时有值） */
    private String rejectReason;
}
