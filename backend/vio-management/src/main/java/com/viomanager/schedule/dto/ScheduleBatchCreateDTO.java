package com.viomanager.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * SC02 批量排班入参（接口清单 §4）。
 * 校验链在 service 层按序执行（8009→8007→8006→8005→8008），
 * force=true 时 8008 冲突项强制保存并打冲突标记（拍板：确认弹窗归前端，后端只认参数）。
 */
@Data
public class ScheduleBatchCreateDTO {

    @NotNull(message = "semesterId 不能为空")
    private Long semesterId;

    @NotNull(message = "weekNo 不能为空")
    @Min(value = 1, message = "weekNo 从 1 起")
    private Integer weekNo;

    @NotNull(message = "subOrgId 不能为空")
    private Long subOrgId;

    @NotEmpty(message = "assignments 不能为空")
    @Valid
    private List<Assignment> assignments;

    /** 课表冲突时是否强制保存（默认 false） */
    private Boolean force = Boolean.FALSE;

    /** 单条排班指派。 */
    @Data
    public static class Assignment {

        @NotNull(message = "memberId 不能为空")
        private Long memberId;

        @NotNull(message = "weekday 不能为空")
        @Min(value = 1, message = "weekday 为 1-7")
        @Max(value = 7, message = "weekday 为 1-7")
        private Integer weekday;

        @NotNull(message = "slotIndex 不能为空")
        @Min(value = 1, message = "slotIndex 为 1-5")
        @Max(value = 5, message = "slotIndex 为 1-5")
        private Integer slotIndex;

        @Size(max = 100, message = "dutyName 最长 100 字")
        private String dutyName;
    }
}
