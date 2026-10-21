package com.viomanager.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 赛队挂载项目实体，对应表 team_project_ref（表在项目域，B 线前端在赛队详情页调用）。
 * 一个项目可同时被多支赛队挂载（打不同比赛）；解绑置 RELEASED，已生成任务不回收。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("team_project_ref")
public class TeamProjectRef extends BaseEntity {

    /** 赛队 ID */
    private Long teamId;

    /** 项目 ID */
    private Long projectId;

    /** 比赛 ID（冗余） */
    private Long contestId;

    /** 挂载时选择的赛道 ID */
    private Long trackId;

    /** 学期 ID（校验同项目同学期，7008） */
    private Long semesterId;

    /** 状态：ACTIVE 挂载中 / RELEASED 已解绑 */
    private String status;

    /** 挂载操作人人员 ID（队长） */
    private Long attachedBy;

    /** 挂载时间 */
    private LocalDateTime attachedAt;

    /** 解绑时间 */
    private LocalDateTime releasedAt;
}
