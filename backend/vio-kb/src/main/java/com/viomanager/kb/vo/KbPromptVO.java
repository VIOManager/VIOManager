package com.viomanager.kb.vo;

import lombok.Data;

/**
 * KB04 AI 提示词列表项：聊天界面一键复用的数据源（PRD §3.14）。
 */
@Data
public class KbPromptVO {

    private Long id;

    private String title;

    private String summary;

    /** 提示词结构模板（JSON 字符串，前端直接复用） */
    private String promptSchema;

    private String category;
}
