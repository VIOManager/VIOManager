package com.viomanager.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 绩效维度得分实体，对应表 performance_score（V1__init_52_tables.sql 第 1098–1115 行）。
 * ⚠️ 本表审计列仅 created_at / updated_at（无 deleted 与 created_by 等），不继承 BaseEntity。
 * weight / max_score / dimension_name 为打分时点快照——规则后续变更不影响历史分（PF07 契约）。
 */
@Data
@Accessors(chain = true)
@TableName("performance_score")
public class PerformanceScore {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 周报 ID */
    private Long weeklyId;

    /** 子组织 ID（冗余，便于直接聚合） */
    private Long subOrgId;

    /** 维度键，对应 performance_rule */
    private String dimensionKey;

    /** 维度名称冗余 */
    private String dimensionName;

    /** 打分时的权重快照 */
    private BigDecimal weight;

    /** 满分快照 */
    private BigDecimal maxScore;

    /** 该维度得分 */
    private BigDecimal score;

    /** 加权分 = score × weight */
    private BigDecimal weightedScore;

    /** 该项评语 */
    private String commentText;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
