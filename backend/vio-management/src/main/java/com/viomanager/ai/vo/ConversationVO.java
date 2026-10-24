package com.viomanager.ai.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI04 会话列表行。
 */
@Data
public class ConversationVO {

    private Long id;

    private String title;

    /** 模型名快照（建会话时配置） */
    private String model;

    /** 消息数 */
    private Integer messageCount;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
}
