package com.viomanager.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 比赛创建/编辑参数（C03 / C04 / P02 confirm body）。
 * 前端字段口径（contest.ts ContestForm）：signupStart/End 对应 register_start/end_at，
 * contact 单字段对应 contact_name + contact_phone（拼接 "姓名 电话"）。
 */
@Data
public class ContestCreateDTO {

    /** 来源模板 ID，手工创建可空 */
    private Long templateId;

    @NotNull(message = "semesterId 不能为空")
    private Long semesterId;

    @NotBlank(message = "比赛名称不能为空")
    @Size(max = 200, message = "比赛名最长 200 字")
    private String name;

    /** 报名开始时间，对应 register_start_at */
    private LocalDateTime signupStart;

    /** 报名截止时间，对应 register_end_at */
    private LocalDateTime signupEnd;

    /** 比赛开始时间 */
    private LocalDateTime contestStart;

    /** 比赛结束时间 */
    private LocalDateTime contestEnd;

    /** 联系人（前端单字段，后端拆为 contact_name + contact_phone） */
    @Size(max = 100, message = "联系人最长 100 字")
    private String contact;

    @Size(max = 65535, message = "描述过长")
    private String description;

    /** 比赛级规则覆盖，键名见 §0，只存被覆盖的键 */
    private Map<String, Object> overrideRules;
}
