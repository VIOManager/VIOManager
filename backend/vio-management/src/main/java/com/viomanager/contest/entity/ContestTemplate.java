package com.viomanager.contest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 比赛模板实体，对应表 contest_template。
 * default_rules 为三级继承的规则基线（§0 键名表），比赛与赛道覆盖仅存被覆盖的键。
 * uk_template_name：模板名不可重复。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("contest_template")
public class ContestTemplate extends BaseEntity {

    /** 模板名称 */
    private String name;

    /** 模板编码 */
    private String code;

    /** 赛事类别，如 电控 / 建模 / 程序设计 */
    private String category;

    /** 模板说明 */
    private String description;

    /** 默认规则 JSON 字符串：{"memberLimit":5,"allowRepeatJoin":false,...} */
    private String defaultRules;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 排序号 */
    private Integer sortNo;
}
