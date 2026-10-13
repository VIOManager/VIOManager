package com.viomanager.achievement.dto;

import lombok.Data;

/**
 * 成果审批入参（A07，本任务先建不挂接口）：action APPROVE 通过 / REJECT 驳回，comment 审批意见。
 */
@Data
public class AchievementReviewDTO {

    /** 审批动作：APPROVE / REJECT */
    private String action;

    /** 审批意见（驳回时必填，通过时可空） */
    private String comment;
}
