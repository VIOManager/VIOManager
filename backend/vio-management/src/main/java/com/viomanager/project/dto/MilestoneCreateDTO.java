package com.viomanager.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 创建比赛时间节点（PR17）：仅部门管理员与超管，落 DRAFT 待提交审核。
 * nodeType 四种取值见清单 §3.4。
 */
@Data
public class MilestoneCreateDTO {

    /** 节点类型：REGISTER_START 报名开始 / REGISTER_END 报名截止 / WORK_SUBMIT 作品提交 / OTHER 其他 */
    @NotBlank(message = "nodeType 不能为空")
    private String nodeType;

    /** 节点名称（如"作品提交截止"） */
    @NotBlank(message = "name 不能为空")
    @Size(max = 200, message = "节点名称最长 200 字")
    private String name;

    /** 节点时间（闹钟日期，同步任务的 due_date 来源） */
    @NotNull(message = "nodeTime 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nodeTime;

    /** 说明（可选） */
    @Size(max = 1000, message = "说明最长 1000 字")
    private String description;
}
