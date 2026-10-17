package com.viomanager.kb.dto;

import lombok.Data;

/**
 * KB01 浏览列表筛选参数（全部可选；返回数组，当前数据量小不分页，见方案文档）。
 */
@Data
public class KbDocQueryDTO {

    /** 分类（字典 KB_CATEGORY） */
    private String category;

    /** 关键词，走 FULLTEXT ngram（title + summary） */
    private String keyword;

    /** 来源筛选：MANUAL / LINK / AI_SUMMARY */
    private String sourceType;
}
