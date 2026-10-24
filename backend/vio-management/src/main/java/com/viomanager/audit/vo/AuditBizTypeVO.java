package com.viomanager.audit.vo;

import lombok.Data;

/**
 * AQ02 业务类型枚举项（筛选下拉用）。
 */
@Data
public class AuditBizTypeVO {

    /** 业务类型代码，如 TEAM / DICT */
    private String bizType;

    /** 业务类型中文名 */
    private String label;
}
