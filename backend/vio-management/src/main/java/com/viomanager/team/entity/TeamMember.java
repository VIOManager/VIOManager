package com.viomanager.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 赛队成员实体，对应表 team_member。
 * 状态机：UNCONFIRMED → CONFIRMED / REJECTED；CONFIRMED → EXITED / REMOVED。
 * (team_id, member_id, deleted) 唯一约束保证一人一队一行有效记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("team_member")
public class TeamMember extends BaseEntity {

    /** 赛队 ID */
    private Long teamId;

    /** 人员档案 ID（member.id） */
    private Long memberId;

    /** 人员姓名冗余 */
    private String memberName;

    /** 人员所属组织冗余 */
    private Long memberOrgId;

    /** 是否队长：1 是 0 否 */
    private Integer isLeader;

    /** 状态：UNCONFIRMED / CONFIRMED / REJECTED / EXITED / REMOVED */
    private String status;

    /** 加入时间（确认时写入） */
    private LocalDateTime joinedAt;

    /** 离开时间 */
    private LocalDateTime leftAt;

    /** 离开原因 */
    private String leftReason;

    /** 加入时的人数与规则快照 JSON */
    private String joinSnapshot;

    /** 备注 */
    private String remark;
}
