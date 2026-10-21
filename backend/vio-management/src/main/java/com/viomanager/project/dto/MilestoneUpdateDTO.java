package com.viomanager.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 编辑时间节点（PR18）：仅 DRAFT 与 REJECTED 可编辑（7009），全部字段可选传。
 * 状态流转不在此接口——提交审核走 PR20 专用端点。
 */
@Data
public class MilestoneUpdateDTO {

    /** 节点类型（可选更新） */
    @Size(max = 64, message = "nodeType 最长 64 字")
    private String nodeType;

    /** 节点名称（可选更新） */
    @Size(max = 200, message = "节点名称最长 200 字")
    private String name;

    /** 节点时间（可选更新） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nodeTime;

    /** 说明（可选更新） */
    @Size(max = 1000, message = "说明最长 1000 字")
    private String description;
}
