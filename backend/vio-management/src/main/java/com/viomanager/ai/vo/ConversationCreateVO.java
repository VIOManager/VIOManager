package com.viomanager.ai.vo;

import lombok.Data;

/**
 * AI03 新建会话出参：单字段 id 走 VO 不用 Map（全仓风格）。
 */
@Data
public class ConversationCreateVO {

    /** 新会话 ID（字符串序列化） */
    private Long id;
}
