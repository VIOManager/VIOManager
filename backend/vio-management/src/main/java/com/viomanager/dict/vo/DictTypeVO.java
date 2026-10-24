package com.viomanager.dict.vo;

import lombok.Data;

/**
 * SY01 字典类型列表项：类型信息 + 条目数/待审核数徽标。
 */
@Data
public class DictTypeVO {

    private Long id;

    /** 字典编码（创建后不可改） */
    private String dictCode;

    /** 字典名称 */
    private String dictName;

    /** 说明 */
    private String remark;

    /** 条目数（未删除的全部条目，含停用/待审核） */
    private Integer itemCount;

    /** 待审核数（用户补充词条） */
    private Integer pendingCount;

    /** 预设类型（建库种子）不可删 */
    private Boolean preset;
}
