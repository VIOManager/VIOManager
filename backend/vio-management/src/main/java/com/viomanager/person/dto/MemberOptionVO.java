package com.viomanager.person.dto;

import lombok.Data;

/**
 * M14 受限候选搜索的出参（队长流程：预填成员 / 指导教师候选）。
 * 刻意只有目录级八字段——无手机号/身份证/邮箱等任何敏感列（查询侧也只 select 这几列），
 * 所有登录角色可用：B 线队长端原先直调 M01（管理员专属）必 403，2026-10-23 增此受限口。
 */
@Data
public class MemberOptionVO {

    private Long id;

    private String name;

    /** STUDENT / TEACHER / OTHER */
    private String memberType;

    private String studentNo;

    private String jobNo;

    private Long orgId;

    private String orgName;

    /** 学院（档案文本字段，M02 受限视图同级别的目录信息，教师候选展示用） */
    private String college;
}
