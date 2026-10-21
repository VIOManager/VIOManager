package com.viomanager.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 编辑任务或更新状态（PR23）：所有人可改全部字段；
 * 任务负责人本人仅可将状态置 DONE（completed_at / completed_by 回填）。
 */
@Data
public class TaskUpdateDTO {

    /** 任务标题（仅所有人可改） */
    @Size(max = 300, message = "任务标题最长 300 字")
    private String title;

    /** 任务说明（仅所有人可改） */
    @Size(max = 1000, message = "任务说明最长 1000 字")
    private String description;

    /** 负责人人员 ID（仅所有人可改） */
    private Long assigneeMemberId;

    /** 截止时间（仅所有人可改） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueDate;

    /** 优先级（仅所有人可改） */
    private String priority;

    /** 排序号（仅所有人可改） */
    private Integer sortNo;

    /** 状态：TODO / DOING / DONE / BLOCKED（负责人本人仅可置 DONE） */
    private String status;
}
