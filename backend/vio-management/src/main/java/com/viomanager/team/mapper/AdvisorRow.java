package com.viomanager.team.mapper;

import lombok.Data;

/**
 * 指导教师行（TE02 详情 / TE11）：team_advisor 关联 member。
 * 移除接口 TE10 的路径 advisorId 即教师 member_id（与前端 TeamAdvisorItem.memberId 对齐）。
 */
@Data
public class AdvisorRow {
    private Long memberId;
    private String advisorName;
    private String title;
    private String college;
}
