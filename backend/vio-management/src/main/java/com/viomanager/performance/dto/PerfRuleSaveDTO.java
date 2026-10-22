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
 * PF12 绩效规则批量保存（upsert）入参：semesterId 空 = 子组织默认规则。
 * 维度键建议大写蛇形（TECH_OUTPUT / COLLABORATION / ATTENDANCE）。
 */
@Data
public class PerfRuleSaveDTO {

    @NotNull(message = "subOrgId 不能为空")
    private Long subOrgId;

    /** 空 = 默认规则（对该子组织所有学期生效） */
    private Long semesterId;

    @NotEmpty(message = "items 不能为空")
    @Valid
    private List<Item> items;

    /** 单维度规则项。 */
    @Data
    public static class Item {

        @NotBlank(message = "dimensionKey 不能为空")
        @Size(max = 64, message = "dimensionKey 最长 64 字")
        private String dimensionKey;

        @NotBlank(message = "dimensionName 不能为空")
        @Size(max = 100, message = "dimensionName 最长 100 字")
        private String dimensionName;

        @NotNull(message = "weight 不能为空")
        @DecimalMin(value = "0.001", message = "weight 须大于 0")
        private BigDecimal weight;

        @NotNull(message = "maxScore 不能为空")
        @DecimalMin(value = "0.01", message = "maxScore 须大于 0")
        private BigDecimal maxScore;

        @NotNull(message = "enabled 不能为空")
        private Boolean enabled;

        private Integer sortNo;

        @Size(max = 500, message = "description 最长 500 字")
        private String description;
    }
}
