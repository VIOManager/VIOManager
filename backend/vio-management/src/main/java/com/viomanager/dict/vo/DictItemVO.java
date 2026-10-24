package com.viomanager.dict.vo;

import lombok.Data;

/**
 * D01 字典读出参（前端枚举下拉统一数据源）。
 * label 是 itemLabel 的别名字段；id / createdByName 为 2026-10-24 补齐——
 * A 线字典审核面板按 label 渲染、用 id 做行键与审核目标、显示"补充人"列，
 * 原先这三样后端都没给（该面板自 A 线联调起实际不可用，本轮收口）。
 */
@Data
public class DictItemVO {

    private Long id;

    /** 字典值 */
    private String itemValue;

    /** 展示文案 */
    private String itemLabel;

    /** 展示文案别名（与 itemLabel 同值，兼容按 label 渲染的页面） */
    private String label;

    /** 来源：PRESET 预设 / USER 用户补充 */
    private String source;

    /** 审核状态：APPROVED / PENDING / REJECTED */
    private String auditStatus;

    /** 排序号 */
    private Integer sortNo;

    /** 用户补充项的补充人姓名（仅管理员调用时填充；学生下拉用不到、也不为学生查用户表） */
    private String createdByName;
}
