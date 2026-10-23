package com.viomanager.performance.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 周报列表项出参（PF01/PF06 共用，前端 WeeklyItem 消费形状）。
 * subOrgId 冗余供前端拉该子组织打分规则（前端注释口径）。
 */
@Data
public class WeeklyItemVO {

    /** 周报 ID */
    private Long id;

    /** 上报人人员 ID */
    private Long memberId;

    /** 姓名 */
    private String memberName;

    /** 子组织 ID */
    private Long subOrgId;

    /** 子组织名 */
    private String subOrgName;

    /** 学期 ID（打分抽屉据此拉该学期的生效规则，与 PF07 校验口径一致） */
    private Long semesterId;

    /** 周次 */
    private Integer weekNo;

    /** 状态：SUBMITTED / SCORED / REJECTED */
    private String status;

    /** 加权总分（未打分为 null） */
    private BigDecimal totalScore;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
}
