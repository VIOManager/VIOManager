package com.viomanager.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI01 发消息入参。conversationId 为空 = 无会话模式（纯流式不落库）；
 * 不挂幂等键：生成型操作天然一次性，断流由前端"重新生成"重发（接口清单 §3）。
 */
@Data
public class ChatDTO {

    /** 所属会话；null = 无会话模式 */
    private Long conversationId;

    /** 用户提问内容 1-500 字 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 500, message = "消息内容最长 500 字")
    private String content;
}
