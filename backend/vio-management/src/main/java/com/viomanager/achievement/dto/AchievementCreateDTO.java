package com.viomanager.achievement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import lombok.Data;

/**
 * 提交成果入参（A03）：学生走队长校验 + 审批流（PENDING/USER），管理员直接录入生效（APPROVED/ADMIN），
 * 分支编排在 AchievementApprovalSupport。与实体字段的名映射：type → achievement_type，achievedAt → award_date。
 */
@Data
public class AchievementCreateDTO {

    /** 赛队 ID */
    private Long teamId;

    /** 成果类型：AWARD 获奖 / RATING 评级 / QUALIFICATION 资质 / ACTIVITY 活动成果 / OTHER 其他 */
    private String type;

    /** 成果名称 */
    private String name;

    /** 级别：国家级 / 省级 / 市级 / 校级 / 院级 等（自由字符串，可空） */
    private String level;

    /** 获奖/取得日期（yyyy-MM-dd） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate achievedAt;

    /** 成果描述（可空） */
    private String description;

    /** 佐证文件 ID（可空，A06 可后补/覆盖） */
    private Long evidenceFileId;

    /** 佐证文件名（可空，与 evidenceFileId 配对） */
    private String evidenceFileName;
}
