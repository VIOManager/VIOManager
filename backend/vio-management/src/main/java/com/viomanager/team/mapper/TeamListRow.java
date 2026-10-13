package com.viomanager.team.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 赛队行（TE01 列表 / TE02 详情共用）：team 关联 contest / track / 队长名 + 成员/指导教师计数。
 * 由 JdbcTemplate 显式列名查询填充；createdAt 仅详情用，列表查询不 select 时为 null。
 */
@Data
public class TeamListRow {
    private Long id;
    private String name;
    private String projectName;
    private Long contestId;
    private String contestName;
    private Long trackId;
    private String trackName;
    private Long leaderMemberId;
    private String leaderName;
    private Integer memberCount;
    private Integer advisorCount;
    private String status;
    private String materialStatus;
    private BigDecimal materialCompleteRate;
    private Long orgId;
    private Long semesterId;
    private LocalDateTime createdAt;
}
