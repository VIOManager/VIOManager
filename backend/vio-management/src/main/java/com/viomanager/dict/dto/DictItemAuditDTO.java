package com.viomanager.dict.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * D03 字典项审核入参：true 通过入库（APPROVED）/ false 驳回（REJECTED）。
 */
@Data
public class DictItemAuditDTO {

    @NotNull(message = "审核结论不能为空")
    private Boolean approve;
}
