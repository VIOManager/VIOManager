package com.viomanager.reply.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * F14 评论状态设置入参：to ∈ {NORMAL, HIDDEN}。删除=HIDDEN、恢复=NORMAL（软删口径）。
 */
@Data
public class ReplyStatusDTO {

    @NotBlank(message = "to 不能为空")
    private String to;
}
