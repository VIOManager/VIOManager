package com.viomanager.contest.mapper;

import lombok.Data;

/**
 * 赛道行（C02 详情内嵌 / C07-C09）：contest_track + teamCount（赛道下赛队数）。
 * 由 JdbcTemplate 显式列名查询填充。
 */
@Data
public class TrackRow {
    private Long id;
    private Long contestId;
    private String name;
    private String code;
    private String overrideRules;
    private String description;
    private Integer sortNo;
    private Integer status;
    /** 该赛道下赛队数 */
    private Integer teamCount;
}
