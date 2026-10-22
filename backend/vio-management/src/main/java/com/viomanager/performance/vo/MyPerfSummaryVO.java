package com.viomanager.performance.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * PF05 我的绩效汇总出参（前端 MyPerfSummary 消费形状，当前学期免参由服务端取 ACTIVE 行）。
 */
@Data
public class MyPerfSummaryVO {

    /** 学期 ID */
    private Long semesterId;

    /** 学期名 */
    private String semesterName;

    /** 累计总分 */
    private BigDecimal totalScore;

    /** 平均分 */
    private BigDecimal avgScore;

    /** 已打分周数 */
    private Integer scoredCount;

    /** 已提交周数 */
    private Integer submitCount;

    /** 最高分周次（无打分为 0） */
    private Integer bestWeekNo;

    /** 最高周分（无打分为 0） */
    private BigDecimal bestWeekScore;

    /** 子组织内排名（无汇总为 0） */
    private Integer rankInOrg;

    /** 子组织内参与人数 */
    private Integer orgMemberCount;

    /** 近 4 周趋势斜率（正=上升，无基线为 0） */
    private BigDecimal trendSlope;

    /** 活跃度/功劳标签 */
    private List<String> tags;
}
