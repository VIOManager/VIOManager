package com.viomanager.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * SC11 排班规则批量保存（upsert）入参：semesterId 空 = 子组织默认规则。
 */
@Data
public class ScheduleRuleSaveDTO {

    @NotNull(message = "subOrgId 不能为空")
    private Long subOrgId;

    /** 空 = 默认规则（对该子组织所有学期生效） */
    private Long semesterId;

    @NotEmpty(message = "items 不能为空")
    @Valid
    private List<Item> items;

    /** 单时段规则项。 */
    @Data
    public static class Item {

        @NotNull(message = "weekday 不能为空")
        @Min(value = 1, message = "weekday 为 1-7")
        @Max(value = 7, message = "weekday 为 1-7")
        private Integer weekday;

        @NotNull(message = "slotIndex 不能为空")
        @Min(value = 1, message = "slotIndex 为 1-5")
        @Max(value = 5, message = "slotIndex 为 1-5")
        private Integer slotIndex;

        @NotNull(message = "minPerson 不能为空")
        @Min(value = 0, message = "minPerson 最小 0")
        private Integer minPerson;

        @NotNull(message = "maxPerson 不能为空")
        @Min(value = 1, message = "maxPerson 最小 1")
        private Integer maxPerson;

        @NotNull(message = "enabled 不能为空")
        private Boolean enabled;

        @jakarta.validation.constraints.Size(max = 500, message = "remark 最长 500 字")
        private String remark;
    }
}
