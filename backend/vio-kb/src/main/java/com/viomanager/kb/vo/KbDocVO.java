package com.viomanager.kb.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库列表卡片（KB01 列表 / KB03 推荐位复用）。
 * ⚠️ 刻意不含 content（LONGTEXT 大字段不进列表，方案"大字段"对策）。
 */
@Data
public class KbDocVO {

    private Long id;

    private String title;

    private String category;

    /** 是否 AI 提示词：1 是 0 否 */
    private Integer isPrompt;

    private String summary;

    /** 来源：MANUAL / LINK / AI_SUMMARY */
    private String sourceType;

    /** 外部链接（LINK 类型跳转用） */
    private String sourceUrl;

    /** 标签，逗号分隔 */
    private String tags;

    /** 是否置顶：1 是 0 否 */
    private Integer topFlag;

    private Integer viewCount;

    private Integer collectCount;

    private Integer commentCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
