package com.viomanager.schedule.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/**
 * TT07 批量确认班级映射入参：确认后该班级参与空课统计（PRD 铁规则：未确认永远不参与）。
 */
@Data
public class ClassMappingConfirmDTO {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
