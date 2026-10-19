package com.viomanager.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目实体，对应表 project（initsql.sql §project，表结构即契约）。
 * 项目是个人资产：owner 拥有完整管理权，与赛队解耦（PRD §3.10 关键定位）。
 *
 * ⚠️ status 流转只允许 ACTIVE→FINISHED→ARCHIVED 顺序，走 ProjectMapper#casStatus 条件更新防并发跳档。
 * ⚠️ visibility 的八条可见性判定收口在 ProjectVisibilitySupport，读接口不得自行实现。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("project")
public class Project extends BaseEntity {

    /** 项目名称（同学期同组织唯一，uk_project_name 拦 7004） */
    private String name;

    /** 项目描述 */
    private String description;

    /** 项目所有人人员 ID（member.id，非 sys_user.id） */
    private Long ownerMemberId;

    /** 所有人姓名冗余（列表展示免 JOIN） */
    private String ownerName;

    /** 所属比赛 ID（可选挂载） */
    private Long contestId;

    /** 所属赛道 ID（可选） */
    private Long trackId;

    /** 所属学期 ID */
    private Long semesterId;

    /** 所属子组织 ID（数据范围与绩效隔离依据） */
    private Long orgId;

    /** 状态：ACTIVE 进行中 / FINISHED 已结束 / ARCHIVED 已归档 */
    private String status;

    /** 比赛时间节点锚点日期 */
    private LocalDateTime milestoneAnchor;

    /** 项目开始日期 */
    private LocalDate startDate;

    /** 项目结束日期 */
    private LocalDate endDate;

    /** 可见范围：PRIVATE 仅所有人 / ORG 本部门 / PUBLIC 全平台 */
    private String visibility;

    /** 备注 */
    private String remark;
}
