package com.viomanager.reply.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * F13 管理员编辑评论入参（2026-10-17 拍板：管理员直接覆盖原文，edited 置 1 透出）。
 */
@Data
public class ReplyUpdateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 5000, message = "评论内容不超过 5000 字")
    private String text;
}
