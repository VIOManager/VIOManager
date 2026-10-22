package com.viomanager.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SC03 编辑单条排班入参：改时段 / 值班内容 / 备注，改时段后重新过校验链。
 */
@Data
public class ScheduleUpdateDTO {

    @Min(value = 1, message = "weekday 为 1-7")
    @Max(value = 7, message = "weekday 为 1-7")
    private Integer weekday;

    @Min(value = 1, message = "slotIndex 为 1-5")
    @Max(value = 5, message = "slotIndex 为 1-5")
    private Integer slotIndex;

    @Size(max = 100, message = "dutyName 最长 100 字")
    private String dutyName;

    @Size(max = 500, message = "remark 最长 500 字")
    private String remark;
}
