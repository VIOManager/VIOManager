package com.viomanager.achievement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import lombok.Data;

/**
 * 编辑成果入参（A04）：只改 name / level / achievedAt / description 四个字段。
 * PUT 全量替换语义——四字段按请求体原样落库，未传的置空，前端编辑页须回填全部字段。
 */
@Data
public class AchievementUpdateDTO {

    /** 成果名称 */
    private String name;

    /** 级别（自由字符串，可空） */
    private String level;

    /** 获奖/取得日期（yyyy-MM-dd） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate achievedAt;

    /** 成果描述（可空） */
    private String description;
}
