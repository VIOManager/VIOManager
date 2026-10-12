package com.viomanager.dict.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 字典项实体，对应表 sys_dict_item。
 * 字典类型表 sys_dict_type 本步用不到（查询直接按 dict_code 打字典项表），
 * D02 用户补充 / D03 审核（A 线实现）需要时再建。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {

    /** 所属字典编码，如 SKILL / MATERIAL_TYPE */
    private String dictCode;

    /** 字典值（存库的编码，如 java） */
    private String itemValue;

    /** 展示文案（下拉框里看到的文字） */
    private String itemLabel;

    /** 来源：PRESET 预设 / USER 用户补充 */
    private String source;

    /** 审核状态：APPROVED 已入库 / PENDING 待审核 / REJECTED 已驳回 */
    private String auditStatus;

    /** 排序号，小的在前 */
    private Integer sortNo;

    /** 状态：1 启用 0 停用（停用的不出现在字典读接口） */
    private Integer status;
}
