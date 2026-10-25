package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.achievement.dto.AchievementQueryDTO;
import com.viomanager.achievement.service.AchievementService;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：赛队成果（清单 §2，登录即可调）——按赛队/类型/状态/关键词查成果记录，
 * 复用 C 线 A 线查询接口，可见范围与本人直调一致。
 */
@Component
public class QueryTeamAchievementsTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AchievementService achievementService;

    @Override
    public String name() {
        return "queryTeamAchievements";
    }

    @Override
    public String description() {
        return "查询赛队成果记录（获奖/评级/晋级/活动），可按赛队 ID、成果类型"
                + "（AWARD/RATING/QUALIFICATION/ACTIVITY/OTHER）、状态（PENDING/APPROVED/REJECTED）、"
                + "成果名称关键词筛选";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "teamId":{"type":"integer","description":"赛队 ID，可选"},
                  "type":{"type":"string","description":"成果类型 AWARD/RATING/QUALIFICATION/ACTIVITY/OTHER，可选"},
                  "status":{"type":"string","description":"状态 PENDING/APPROVED/REJECTED，可选"},
                  "keyword":{"type":"string","description":"成果名称关键词，可选"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        AchievementQueryDTO dto = new AchievementQueryDTO();
        if (args.path("teamId").isNumber()) {
            dto.setTeamId(args.path("teamId").asLong());
        }
        if (args.path("type").isTextual()) {
            dto.setType(args.path("type").asText());
        }
        if (args.path("status").isTextual()) {
            dto.setStatus(args.path("status").asText());
        }
        if (args.path("keyword").isTextual()) {
            dto.setKeyword(args.path("keyword").asText());
        }
        return objectMapper.writeValueAsString(achievementService.list(dto));
    }
}
