package com.viomanager.team.vo;

import lombok.Data;

/**
 * 赛队指导教师出参（TE02 详情 / TE11），字段口径 = 前端 TeamAdvisorItem。
 * 指导教师信息全员可见（不裁剪），仅 ACTIVE 状态下发。
 */
@Data
public class TeamAdvisorVO {

    private Long memberId;
    private String name;
    private String college;
}
