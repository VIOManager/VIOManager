package com.viomanager.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * 赛道创建/编辑参数（C07 / C08 body）。
 * overrideRules 为赛道级规则覆盖，只存被覆盖的键。
 */
@Data
public class TrackCreateDTO {

    @NotBlank(message = "赛道名称不能为空")
    @Size(max = 200, message = "赛道名最长 200 字")
    private String name;

    @Size(max = 64, message = "编码最长 64 字")
    private String code;

    @Size(max = 1000, message = "说明最长 1000 字")
    private String description;

    /** 赛道级规则覆盖 */
    private Map<String, Object> overrideRules;
}
