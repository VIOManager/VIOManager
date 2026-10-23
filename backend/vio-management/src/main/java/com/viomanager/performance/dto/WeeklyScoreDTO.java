package com.viomanager.performance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * PF07 打分入参。校验（service 层）：维度集合须与该子组织 enabled 规则完全一致（8043）、
 * 各维度不超满分（8044）、未配置规则拒绝（8046）；落库时快照当时的 weight 与 max_score。
 */
@Data
public class WeeklyScoreDTO {

    @NotEmpty(message = "scores 不能为空")
    @Valid
    private List<ScoreLine> scores;

    @Size(max = 1000, message = "scoreComment 最长 1000 字")
    private String scoreComment;

    /** 单维度打分项。 */
    @Data
    public static class ScoreLine {

        @NotBlank(message = "dimensionKey 不能为空")
        private String dimensionKey;

        @NotNull(message = "score 不能为空")
        @DecimalMin(value = "0", message = "score 最小 0")
        private BigDecimal score;

        @Size(max = 500, message = "commentText 最长 500 字")
        private String commentText;
    }
}
