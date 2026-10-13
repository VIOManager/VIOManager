package com.viomanager.team.mapper;

import lombok.Data;

/**
 * 赛队成员行（TE11）：team_member 关联 member 取展示字段。
 * 字段裁剪在 service 按 viewerRole 做college/className/phone/idCardLast4 等敏感字段不在此全量下发。
 */
@Data
public class MemberRow {
    private Long memberId;
    private String name;
    private Integer isLeader;
    private String status;
    private Long memberOrgId;
    private String studentNo;
    private String college;
    private String className;
    private String phone;
    private String idCardLast4;
}
