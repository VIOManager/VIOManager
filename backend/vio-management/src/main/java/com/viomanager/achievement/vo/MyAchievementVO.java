package com.viomanager.achievement.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 学生端"我的成果"出参（A08）：参赛记录 + 获奖记录两段。
 * 🔴 两段都从赛队成员关系与成果表动态聚合，不读任何冗余表。
 */
@Data
public class MyAchievementVO {

    /** 我在册且 ACTIVE 的队伍（含队长标识），默认空列表（无队伍时前端拿到 [] 而非 null） */
    private List<TeamBrief> teams = new ArrayList<>();

    /** 这些队伍的全部成果，默认空列表 */
    private List<AchievementVO> items = new ArrayList<>();

    /** 队伍摘要行 */
    @Data
    @Accessors(chain = true)
    public static class TeamBrief {

        /** 赛队 ID（JSON 序列化为字符串） */
        private Long teamId;

        /** 队名 */
        private String teamName;

        /** 当前用户是否该队队长（包装类保住 setIsLeader 命名与 JSON 字段名 isLeader） */
        private Boolean isLeader;
    }
}
