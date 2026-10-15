package com.viomanager.kb.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库列表卡片（KB01 列表 / KB03 推荐位 / KB17 管理列表 / KB12 创建返回复用）。
 * 字段对齐前端 KbItem（2026-10-14 拍板简化：链接或文件二选一，无分类/正文/标签）。
 * ⚠️ 刻意不含 content（LONGTEXT 大字段不进列表，方案"大字段"对策）。
 */
@Data
public class KbDocVO {

    private Long id;

    private String title;

    private String summary;

    /** 条目形态：LINK / FILE */
    private String sourceType;

    /** 外部链接（LINK 时有值） */
    private String linkUrl;

    /** 附件文件 ID（FILE 时有值；下载走骨架 GET /api/files/{fileId}/download） */
    private Long fileId;

    /** 附件原始文件名（FILE 时列表展示与下载兜底） */
    private String fileName;

    /** 是否置顶 */
    private Boolean pinned;

    private Integer viewCount;

    /** 收藏数（前端字段名 favoriteCount，落列 collect_count） */
    private Integer favoriteCount;

    private Integer commentCount;

    /** 发布人姓名（JWT realName 快照冗余） */
    private String publisherName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
