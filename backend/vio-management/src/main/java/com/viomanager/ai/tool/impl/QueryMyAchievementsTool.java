package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.achievement.service.AchievementService;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：个人贡献（清单 §2，仅本人域）——参赛 + 获奖汇总，复用 C 线 myContributions。
 */
@Component
public class QueryMyAchievementsTool implements AiTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private AchievementService achievementService;

    @Override
    public String name() {
        return "queryMyAchievements";
    }

    @Override
    public String description() {
        return "查询当前登录用户本人的个人贡献汇总：参加过的比赛、赛队与获奖记录；无需参数";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        return MAPPER.writeValueAsString(achievementService.myContributions());
    }
}
