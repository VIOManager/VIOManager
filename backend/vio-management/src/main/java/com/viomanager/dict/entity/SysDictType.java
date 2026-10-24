package com.viomanager.dict.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 字典类型实体（表 sys_dict_type，标准基座尾列齐全）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    /** 字典编码，如 SKILL / MATERIAL_TYPE（创建后不可改） */
    private String dictCode;

    /** 字典名称 */
    private String dictName;

    /** 说明 */
    private String remark;
}
