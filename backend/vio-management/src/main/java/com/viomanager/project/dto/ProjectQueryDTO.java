package com.viomanager.project.dto;

import lombok.Data;

/**
 * PR01 项目列表筛选。数据范围不在此表达——按角色自动（超管全部/部门管理员子树/学生参与+授权）。
 */
@Data
public class ProjectQueryDTO {

    /** 名称模糊 */
    private String keyword;

    /** 状态：ACTIVE / FINISHED / ARCHIVED */
    private String status;

    /** 所属比赛 */
    private Long contestId;

    /** 所属学期 */
    private Long semesterId;

    /** 所属组织（管理端筛选用） */
    private Long orgId;

    private Integer page;

    private Integer size;
}
