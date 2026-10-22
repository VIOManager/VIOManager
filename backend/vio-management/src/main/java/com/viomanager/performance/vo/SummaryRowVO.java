package com.viomanager.performance.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * PF09 汇总行出参（评优参考，按 totalScore 降序，前端 SummaryRow 消费形状）。
 */
@Data
public class SummaryRowVO {

    /** 人员 ID */
    private Long memberId;

    /** 姓名 */
    private String memberName;

    /** 子组织名 */
    private String subOrgName;

    /** 已提交周数 */
    private Integer submitCount;

    /** 已打分周数 */
    private Integer scoredCount;

    /** 累计总分 */
    private BigDecimal totalScore;

    /** 平均分 */
    private BigDecimal avgScore;

    /** 最高分周次 */
    private Integer bestWeekNo;

    /** 子组织内排名 */
    private Integer rankInOrg;

    /** 活跃度/功劳标签 */
    private List<String> tags;
}
