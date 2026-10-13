package com.viomanager.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 赛队指导教师实体，对应表 team_advisor。
 * member_id 须为 member_type=TEACHER 的人员；指导教师只读、权限不低于队长。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("team_advisor")
public class TeamAdvisor extends BaseEntity {

    /** 赛队 ID */
    private Long teamId;

    /** 指导教师人员档案 ID（member_type=TEACHER） */
    private Long memberId;

    /** 教师姓名冗余 */
    private String advisorName;

    /** 职称，如 教授 / 副教授 */
    private String title;

    /** 状态：ACTIVE 在任 / REMOVED 已移除 */
    private String status;

    /** 移除时间 */
    private LocalDateTime removedAt;

    /** 备注 */
    private String remark;
}
