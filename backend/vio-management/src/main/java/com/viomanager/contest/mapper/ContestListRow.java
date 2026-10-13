package com.viomanager.contest.mapper;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 比赛列表行（C01）：contest 关联 semester 取学期名 + 赛道数 + 赛队数。
 * 由 JdbcTemplate 显式列名查询填充，禁 SELECT *。
 */
@Data
public class ContestListRow {
    private Long id;
    private String name;
    private Long semesterId;
    private String semesterName;
    private Long templateId;
    private Long orgId;
    private String status;
    private LocalDateTime registerStartAt;
    private LocalDateTime registerEndAt;
    private LocalDateTime contestStartAt;
    private LocalDateTime contestEndAt;
    private String contactName;
    private String contactPhone;
    private String description;
    private String overrideRules;
    private String sourceType;
    private Integer trackCount;
    private Integer teamCount;
}
