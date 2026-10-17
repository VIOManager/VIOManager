package com.viomanager.kb.dto;

import lombok.Data;

/**
 * KB17 管理视角列表筛选与分页参数（含 DRAFT/OFFLINE）。
 */
@Data
public class KbAdminQueryDTO {

    /** 状态筛选：DRAFT / PUBLISHED / OFFLINE（空=全部） */
    private String status;

    /** 分类筛选 */
    private String category;

    /** 关键词（FULLTEXT ngram） */
    private String keyword;

    /** 页码，从 1 起（规范 §2.3） */
    private Integer page = 1;

    /** 每页条数，默认 20 上限 100（规范 §2.3） */
    private Integer size = 20;
}
