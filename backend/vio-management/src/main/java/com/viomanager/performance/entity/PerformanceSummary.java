package com.viomanager.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 绩效周期汇总实体，对应表 performance_summary（V1__init_52_tables.sql 第 1117–1141 行）。
 * 由 PF07 打分事务内同步重算（提交数/打分数/总分/平均分/最高周/近 4 周趋势斜率/子组织内排名/标签），
 * 不单独提供写接口。UNIQUE KEY (member_id, semester_id, sub_org_id, deleted)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("performance_summary")
public class PerformanceSummary extends BaseEntity {

    /** 人员 ID */
    private Long memberId;

    /** 姓名冗余 */
    private String memberName;

    /** 学期 ID */
    private Long semesterId;

    /** 子组织 ID */
    private Long subOrgId;

    /** 归属组织（数据范围过滤，= sub_org_id） */
    private Long orgId;

    /** 已打分周数 */
    private Integer weekCount;

    /** 已提交周数 */
    private Integer submittedCount;

    /** 累计总分 */
    private BigDecimal totalScore;

    /** 平均分 */
    private BigDecimal avgScore;

    /** 最高周分 */
    private BigDecimal bestWeekScore;

    /** 最高分所在周次 */
    private Integer bestWeekNo;

    /** 近 4 周分数趋势斜率（正=上升） */
    private BigDecimal recentTrend;

    /** 本子组织内排名 */
    private Integer rankInOrg;

    /** 活跃度/功劳标签 JSON 数组 */
    private String activityTags;

    /** 最后聚合计算时间 */
    private LocalDateTime lastCalcAt;
}
