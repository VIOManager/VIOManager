package com.viomanager.contest.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 比赛详情出参（C02）。
 * rules 为比赛级生效规则（模板+比赛合并），tracks 为赛道列表（每项含赛道级生效规则）。
 * 前端字段口径：signupStart/End 对应 register_start/end_at，contact 拼接 contact_name + contact_phone。
 */
@Data
public class ContestDetailVO {
    private Long id;
    private String name;
    private Long semesterId;
    private String semesterName;
    private Long templateId;
    private String templateName;
    private String status;
    private LocalDateTime signupStart;
    private LocalDateTime signupEnd;
    private LocalDateTime contestStart;
    private LocalDateTime contestEnd;
    /** 联系人（contact_name + contact_phone 拼接） */
    private String contact;
    private String description;
    /** 原始比赛覆盖规则 */
    private Map<String, Object> overrideRules;
    /** 比赛级生效规则（模板+比赛合并） */
    private List<EffectiveRuleVO> rules;
    private List<TrackVO> tracks;
}
