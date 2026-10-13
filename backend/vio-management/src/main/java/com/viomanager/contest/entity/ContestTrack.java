package com.viomanager.contest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 比赛赛道实体，对应表 contest_track。
 * uk_track：同比赛下赛道名唯一。override_rules 为赛道级规则覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("contest_track")
public class ContestTrack extends BaseEntity {

    /** 所属比赛 ID */
    private Long contestId;

    /** 赛道名称 */
    private String name;

    /** 赛道编码 */
    private String code;

    /** 赛道级规则覆盖 JSON */
    private String overrideRules;

    /** 赛道说明 */
    private String description;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
