package com.viomanager.dict.dto;

import lombok.Data;

/**
 * 新增字典条目（D02 用户补充与 SY06 超管预置共用一个口，服务层按角色分流）。
 * 两套前端的 body 形态不同，这里做兼容收口（2026-10-24）：
 * - 系统配置页（system.ts）发 {itemValue, itemLabel, sortNo?}
 * - A 线字典审核面板（dict.ts）只发 {label}——值缺省取显示名
 * 必填校验挪到服务层（别名字段解析后再判空）。
 */
@Data
public class DictItemCreateDTO {

    /** 字典值（存库编码；不传时取 itemLabel/label 同值） */
    private String itemValue;

    /** 展示文案 */
    private String itemLabel;

    /** A 线前端别名字段：只发 label 时值与显示名同值 */
    private String label;

    /** 排序号（SY06 超管可选；用户补充路径忽略、排最大号之后） */
    private Integer sortNo;
}
