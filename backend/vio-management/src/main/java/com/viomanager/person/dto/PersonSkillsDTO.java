package com.viomanager.person.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * M12 技能标签入参：整组替换。值必须来自 SKILL 字典（服务层校验）。
 */
@Data
public class PersonSkillsDTO {

    /** 技能标签值列表（SKILL 字典 item_value；空列表 = 清空全部标签） */
    @NotNull(message = "skills 不能为空")
    private List<String> skills;
}
