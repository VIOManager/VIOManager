package com.viomanager.contest.mapper;

import lombok.Data;

/**
 * 模板列表行（T01）：contest_template + refCount（被比赛引用计数）。
 * 由 JdbcTemplate 显式列名查询填充；refCount > 0 时禁止删除（4012）。
 */
@Data
public class TemplateListRow {
    private Long id;
    private String name;
    private String code;
    private String category;
    private String description;
    private String defaultRules;
    /** 1 启用 0 停用 */
    private Integer status;
    private Integer sortNo;
    /** 被比赛引用计数 */
    private Integer refCount;
}
