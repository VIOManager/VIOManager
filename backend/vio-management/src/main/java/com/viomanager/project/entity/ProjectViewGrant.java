package com.viomanager.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目查看授权实体，对应表 project_view_grant。
 * 走审批中心 PROJECT_VIEW：申请落 PENDING → 审批通过转 ACTIVE → ONCE 用完 / LIMITED 到期置 EXPIRED。
 *
 * ⚠️ ONCE 消耗（PR03 详情触发）必须单语句条件更新：消耗与用完置失效原子完成，防并发双耗。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("project_view_grant")
public class ProjectViewGrant extends BaseEntity {

    /** 项目 ID */
    private Long projectId;

    /** 申请人人员 ID */
    private Long applicantId;

    /** 申请人姓名冗余 */
    private String applicantName;

    /** 申请人所属组织（跨部门判定） */
    private Long applicantOrgId;

    /** 关联审批单 ID */
    private Long approvalId;

    /** 授权类型：ONCE 一次性 / LIMITED 限时 */
    private String grantType;

    /** 限时授权的到期时间 */
    private LocalDateTime expireAt;

    /** 已使用次数 */
    private Integer usedCount;

    /** 状态：PENDING 待审批 / ACTIVE 生效中 / EXPIRED 已失效 / REVOKED 已撤销 */
    private String status;
}
