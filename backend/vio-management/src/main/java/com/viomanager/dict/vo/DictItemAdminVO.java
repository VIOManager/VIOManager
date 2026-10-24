package com.viomanager.dict.vo;

import lombok.Data;

/**
 * SY05 字典条目管理视角列表项（含停用/待审核项）。
 * status 出参用字符串 ENABLED/DISABLED（前端契约），库内是 1/0。
 */
@Data
public class DictItemAdminVO {

    private Long id;

    /** 字典值（存库编码） */
    private String itemValue;

    /** 显示名 */
    private String itemLabel;

    /** 排序号 */
    private Integer sortNo;

    /** 来源：PRESET 预设 / USER 用户补充 */
    private String source;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 审核状态：APPROVED / PENDING / REJECTED */
    private String auditStatus;

    /** 用户补充项的补充人姓名 */
    private String createdByName;
}
