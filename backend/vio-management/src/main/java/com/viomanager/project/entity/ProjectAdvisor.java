package com.viomanager.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目指导教师实体，对应表 project_advisor。
 * 指导教师挂名只读：可见但不可改（可见性判定 §2 第 2 条）；须 member_type=TEACHER（7006）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("project_advisor")
public class ProjectAdvisor extends BaseEntity {

    /** 项目 ID */
    private Long projectId;

    /** 教师人员 ID */
    private Long memberId;

    /** 教师姓名冗余 */
    private String advisorName;

    /** 职称 */
    private String title;

    /** 状态：ACTIVE 在任 / REMOVED 已移除 */
    private String status;
}
