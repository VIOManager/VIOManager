package com.viomanager.team.vo;

import lombok.Data;

/**
 * 赛队成员出参（TE02 详情 / TE11 成员列表），字段口径 = 前端 TeamMemberItem。
 * college / className / phone / idCardLast4 为敏感字段，后端按 viewerRole 裁剪：
 *  LEADER / ADVISOR / ADMIN 见全量；MEMBER 仅本人全量、他人只 memberId/name/role/status。
 */
@Data
public class TeamMemberVO {

    private Long memberId;
    private String name;
    /** LEADER 队长 / MEMBER 队员 */
    private String role;
    /** UNCONFIRMED / CONFIRMED / REJECTED / EXITED / REMOVED */
    private String status;
    /** 学院（敏感，按视角裁剪） */
    private String college;
    /** 班级（敏感，按视角裁剪） */
    private String className;
    /** 手机号（敏感，按视角裁剪） */
    private String phone;
    /** 身份证后 4 位（敏感，按视角裁剪） */
    private String idCardLast4;
}
