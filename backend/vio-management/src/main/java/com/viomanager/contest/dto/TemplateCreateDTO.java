package com.viomanager.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * 比赛模板创建/编辑参数（T02 / T04 body）。
 * defaultRules 键名见 §0（memberLimit/prefillLimit/allowRepeatJoin/crossTeamLeader/maxAdvisorCount/crossTrackLimit）。
 */
@Data
public class TemplateCreateDTO {

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名最长 200 字")
    private String name;

    @Size(max = 64, message = "编码最长 64 字")
    private String code;

    @Size(max = 64, message = "类别最长 64 字")
    private String category;

    @Size(max = 1000, message = "说明最长 1000 字")
    private String description;

    /** 默认规则，键名见 §0；传 null 清空 */
    private Map<String, Object> defaultRules;
}
