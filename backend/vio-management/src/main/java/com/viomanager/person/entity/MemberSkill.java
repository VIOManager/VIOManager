package com.viomanager.person.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 人员技能标签实体，对应表 member_skill。
 * 人员的"技能标签"不在 member 主表，按建表脚本拆为本表：一行一个标签，
 * skill_value 对应 sys_dict_item（SKILL 字典）的 item_value，skill_label 冗余展示文案避免联表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("member_skill")
public class MemberSkill extends BaseEntity {

    /**
     * 人员档案 ID，引用 member.id
     */
    private Long memberId;

    /**
     * 技能值，对应 sys_dict_item.item_value（SKILL 字典）
     */
    private String skillValue;

    /**
     * 技能展示名，冗余避免联表
     */
    private String skillLabel;
}
