package com.viomanager.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 赛队实体，对应表 team。
 * 队名在比赛下唯一（uk_team_name_contest）；队长冗余 leader_member_id 便于快速查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("team")
public class Team extends BaseEntity {

    /** 队名，同比赛下唯一 */
    private String name;

    /** 项目名 */
    private String projectName;

    /** 所属比赛 ID */
    private Long contestId;

    /** 所属赛道 ID */
    private Long trackId;

    /** 所属学期 ID */
    private Long semesterId;

    /** 所属组织 ID（部门管理员数据范围依据） */
    private Long orgId;

    /** 队长人员档案 ID，冗余便于快速查询与唯一性校验 */
    private Long leaderMemberId;

    /** 状态：ACTIVE 正常 / DISSOLVED 已解散 */
    private String status;

    /** 材料状态：NOT_STARTED / PENDING / SUBMITTED */
    private String materialStatus;

    /** 材料齐全率百分数，如 66.67 */
    private BigDecimal materialCompleteRate;

    /** 解散时间 */
    private LocalDateTime dissolvedAt;

    /** 解散原因 */
    private String dissolveReason;

    /** 备注 */
    private String remark;
}
