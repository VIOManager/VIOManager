package com.viomanager.performance.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 周报详情出参（PF04，前端 WeeklyDetail 消费形状）：SCORED 后含各维度得分明细与评语。
 */
@Data
public class WeeklyDetailVO extends WeeklyItemVO {

    /** 本周工作内容 */
    private String workContent;

    /** 附件文件 ID */
    private Long attachFileId;

    /** 附件名 */
    private String attachFileName;

    /** 打分总评（SCORED 后有值） */
    private String scoreComment;

    /** 驳回原因（REJECTED 后有值） */
    private String rejectReason;

    /** 各维度得分明细（快照：打分当时的满分与权重） */
    private List<ScoreLineVO> scores;

    /** 单维度得分行（前端 ScoreLine 消费形状）。 */
    @lombok.Data
    public static class ScoreLineVO {

        private String dimensionKey;

        private String dimensionName;

        private BigDecimal score;

        private BigDecimal maxScore;

        private BigDecimal weight;

        private String commentText;
    }
}
