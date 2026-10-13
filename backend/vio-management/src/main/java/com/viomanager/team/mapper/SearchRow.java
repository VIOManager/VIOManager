package com.viomanager.team.mapper;

import lombok.Data;

/**
 * 加人搜索行（TE05）：姓名模糊匹配 member，标记 inTeam（是否已在当前队伍）。
 */
@Data
public class SearchRow {
    private Long memberId;
    private String name;
    private String studentNo;
    private String college;
    /** 是否已在当前队伍（已确认/未确认均算，前端置灰） */
    private Boolean inTeam;
}
