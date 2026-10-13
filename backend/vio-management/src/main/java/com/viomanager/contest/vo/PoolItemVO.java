package com.viomanager.contest.vo;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 待处理比赛池列表项出参（P01）。
 * 字段口径 = 前端 contest.ts PoolItem：clues 为解析线索（时间/人数上限等）。
 */
@Data
public class PoolItemVO {
    private Long id;
    private String title;
    private String source;
    private LocalDateTime fetchedAt;
    private String content;
    /** 解析线索：signupTime/memberLimit/organizer */
    private Map<String, Object> clues;
    private String status;
    private Long confirmedContestId;
}
