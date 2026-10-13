package com.viomanager.team.vo;

import lombok.Data;

/**
 * 加人搜索出参（TE05），字段口径 = 前端 TeamMemberSearchItem。
 * inTeam 标记供前端置灰（已在本队的不再可加）。
 */
@Data
public class TeamMemberSearchVO {

    private Long memberId;
    private String name;
    private String studentNo;
    private String college;
    private Boolean inTeam;
}
