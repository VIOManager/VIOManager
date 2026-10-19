package com.viomanager.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目成员实体，对应表 project_member。
 * 添加成员不走审批（PRD §3.9 审批清单不含项目加人）；OWNER 行不可移除（7002）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("project_member")
public class ProjectMember extends BaseEntity {

    /** 项目 ID */
    private Long projectId;

    /** 人员档案 ID */
    private Long memberId;

    /** 姓名冗余 */
    private String memberName;

    /** 所属组织冗余（跨部门成员识别） */
    private Long memberOrgId;

    /** 项目角色：OWNER 所有人 / MEMBER 成员 */
    private String projectRole;

    /** 加入时间 */
    private LocalDateTime joinAt;

    /** 备注 */
    private String remark;
}
