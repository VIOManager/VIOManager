package com.viomanager.achievement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import lombok.Data;

/**
 * 成果列表项出参（A01 / A03 / A08 共用），字段口径 = C 线接口清单 §4。
 * 与实体的名映射：type ← achievement_type，achievedAt ← award_date，submittedBy(姓名) ← owner_name。
 */
@Data
public class AchievementVO {

    /** 成果 ID（JSON 序列化为字符串） */
    private Long id;

    /** 赛队 ID */
    private Long teamId;

    /** 队名（achievement 行不存，渲染时批量查 team 表） */
    private String teamName;

    /** 成果类型：AWARD / RATING / QUALIFICATION / ACTIVITY / OTHER */
    private String type;

    /** 成果名称 */
    private String name;

    /** 级别（自由字符串） */
    private String level;

    /** 获奖/取得日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate achievedAt;

    /** 状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 */
    private String status;

    /** 提交人姓名（owner_name 冗余列） */
    private String submittedBy;

    /** 是否已有佐证材料（evidence_file_id 非空即有） */
    private boolean hasEvidence;
}
