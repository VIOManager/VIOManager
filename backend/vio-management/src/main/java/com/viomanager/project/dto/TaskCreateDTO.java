package com.viomanager.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 创建手工任务（PR22）：milestone_node_id 恒空（同步任务只由节点审核生成）。
 * 仅项目所有人可建；负责人可选（须人员档案存在）。
 */
@Data
public class TaskCreateDTO {

    /** 任务标题 */
    @NotBlank(message = "title 不能为空")
    @Size(max = 300, message = "任务标题最长 300 字")
    private String title;

    /** 任务说明 */
    @Size(max = 1000, message = "任务说明最长 1000 字")
    private String description;

    /** 负责人人员 ID（可选） */
    private Long assigneeMemberId;

    /** 截止时间（可选） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueDate;

    /** 优先级：HIGH / NORMAL / LOW（默认 NORMAL） */
    private String priority;

    /** 排序号（默认 0） */
    private Integer sortNo;
}
