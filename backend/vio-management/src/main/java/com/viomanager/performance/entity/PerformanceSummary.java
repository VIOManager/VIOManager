package com.viomanager.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 绩效周期汇总实体，对应表 performance_summary（V1__init_52_tables.sql 第 1117–1141 行）。
 * 由 PF07 打分事务内经 PerfCalcSupport 重算落库；查询侧 PF05（本人）/PF09（管理端按子树）。
 * UNIQUE KEY (member_id, semester_id, sub_org_id, deleted)。
 *
 * ⚠️ V1 中本表审计列仅 deleted / created_at / updated_at（无 created_by / updated_by），
 * 不继承 BaseEntity（BaseEntity javadoc 约定：审计列不全的表自行定义字段）。
 * 注：docs/db/initsql.sql 的本表定义含 created_by/updated_by，与 V1 不一致——
 * 以实际生效的 V1 为准；差异需组长走契约变更流程对齐（冒烟测试 2026-10-14 发现）。
 */
@Data
@Accessors(chain = true)
@TableName("performance_summary")
public class PerformanceSummary {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

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

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 创建时间（库默认值填充） */
    private LocalDateTime createdAt;

    /** 更新时间（库 ON UPDATE 填充） */
    private LocalDateTime updatedAt;
}
