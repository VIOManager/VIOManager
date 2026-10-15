package com.viomanager.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 提交查看申请（PR29）：grantType ONCE 一次性 / LIMITED 限时；
 * LIMITED 必填 expireAt（未来时间），ONCE 忽略该字段。
 * 落 project_view_grant（PENDING）并开 PROJECT_VIEW 审批单。
 */
@Data
public class ViewRequestCreateDTO {

    /** 授权类型：ONCE / LIMITED */
    @NotBlank(message = "grantType 不能为空")
    private String grantType;

    /** 限时截止（LIMITED 必填且须晚于当前时间） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireAt;
}
