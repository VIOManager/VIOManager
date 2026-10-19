package com.viomanager.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目任务列表项（PR21）：milestoneNodeId 非空即节点同步任务（前端区分展示）。
 */
@Data
public class TaskVO {

    /** 任务 ID */
    private Long id;

    /** 任务标题 */
    private String title;

    /** 任务说明 */
    private String description;

    /** 负责人人员 ID */
    private Long assigneeMemberId;

    /** 负责人姓名冗余 */
    private String assigneeName;

    /** 截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueDate;

    /** 状态：TODO / DOING / DONE / BLOCKED */
    private String status;

    /** 优先级：HIGH / NORMAL / LOW */
    private String priority;

    /** 来源时间节点 ID（手工任务为空） */
    private Long milestoneNodeId;

    /** 排序号 */
    private Integer sortNo;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
}
