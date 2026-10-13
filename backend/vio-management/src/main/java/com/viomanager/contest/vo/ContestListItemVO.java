package com.viomanager.contest.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 比赛列表项出参（C01）。
 * 字段口径 = 前端 contest.ts ContestListItem：signupStart/End 对应 register_start/end_at。
 * 学生仅可见 PUBLISHED 及之后状态。
 */
@Data
public class ContestListItemVO {
    private Long id;
    private String name;
    private Long semesterId;
    private String semesterName;
    private String status;
    private LocalDateTime signupStart;
    private LocalDateTime signupEnd;
    private LocalDateTime contestStart;
    private LocalDateTime contestEnd;
    private Integer trackCount;
    private Integer teamCount;
}
