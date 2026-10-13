package com.viomanager.contest.vo;

import java.util.Map;
import lombok.Data;

/**
 * 比赛模板出参（T01 列表项 / T03 详情共用）。
 * 字段口径 = 前端 contest.ts ContestTemplate：enabled 对应 status(1→true)，
 * refCount 为被比赛引用计数（>0 时禁止删除 4012）。
 * ID 字段 Long 经 JacksonConfig 全局序列化为字符串。
 */
@Data
public class TemplateVO {
    private Long id;
    private String name;
    private String code;
    private String category;
    private String description;
    /** 默认规则对象，键名见 §0 */
    private Map<String, Object> defaultRules;
    /** 是否启用 */
    private Boolean enabled;
    /** 被比赛引用计数 */
    private Integer refCount;
}
