package com.viomanager.material.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 编辑材料任务入参（MT03）：只改 name / description / deadline 三个字段。
 * PUT 全量替换语义——三个字段按请求体原样落库，未传的会被置空
 * （deadline 置空即恢复"不限期"），前端编辑页须回填全部字段再提交。
 */
@Data
public class MaterialTaskUpdateDTO {

    /** 任务名称 */
    @NotBlank(message = "name 不能为空")
    private String name;

    /** 任务说明 */
    private String description;

    /** 截止时间，可空（为空视为不限期） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime deadline;
}
