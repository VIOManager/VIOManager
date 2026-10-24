package com.viomanager.ai.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI05 消息历史行。首版只回 user / assistant 两类对话消息；
 * tool 消息是模型内部过程，不进前端历史（接口清单 §4 口径 role = user / assistant）。
 */
@Data
public class MessageVO {

    private Long id;

    /** user / assistant */
    private String role;

    private String content;

    /** 1 = 回复可能不完整（中断/截断），前端展示警示条 */
    private Integer incomplete;

    private LocalDateTime createdAt;
}
