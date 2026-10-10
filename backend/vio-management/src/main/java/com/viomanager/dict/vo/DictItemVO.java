package com.viomanager.dict.vo;

import lombok.Data;

/**
 * 字典项出参，字段口径 = 骨架接口清单 D01：
 * itemValue itemLabel source auditStatus sortNo（刻意不带 id，前端用不到）。
 */
@Data
public class DictItemVO {

    /** 字典值 */
    private String itemValue;

    /** 展示文案 */
    private String itemLabel;

    /** 来源：PRESET 预设 / USER 用户补充 */
    private String source;

    /** 审核状态：APPROVED / PENDING / REJECTED */
    private String auditStatus;

    /** 排序号 */
    private Integer sortNo;
}
