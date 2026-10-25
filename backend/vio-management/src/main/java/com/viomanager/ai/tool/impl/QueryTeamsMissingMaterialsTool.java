package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.result.Result;
import com.viomanager.material.service.MaterialCompletionService;
import com.viomanager.material.vo.TeamMaterialRowVO;
import com.viomanager.team.service.ITeamQueryService;
import com.viomanager.team.vo.TeamListItemVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 白名单工具：未交材料赛队（清单 §2，管理角色）——C 线没有"整场未交齐"列表接口，
 * 这里组合：B 线 listTeams 拿队伍 + C 线 teamOverview 逐队看齐全率，过滤未交齐的。
 * 队伍量级为百人团队的几十队，逐队查可接受。
 */
@Component
public class QueryTeamsMissingMaterialsTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ITeamQueryService teamQueryService;

    @Resource
    private MaterialCompletionService completionService;

    @Override
    public String name() {
        return "queryTeamsMissingMaterials";
    }

    @Override
    public String description() {
        return "列出材料未交齐的赛队及其缺少的材料项（按当前比赛材料要求逐队核对齐全率）；仅管理角色可用";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        if (!AiToolRoles.isAdmin(user)) {
            return AiToolRoles.DENIED_JSON;
        }
        Result<List<TeamListItemVO>> teamsResult = teamQueryService.listTeams(null, null);
        List<TeamListItemVO> teams = teamsResult.getData();
        ArrayNode missing = objectMapper.createArrayNode();
        if (teams != null) {
            for (TeamListItemVO team : teams) {
                Result<TeamMaterialRowVO> overview = completionService.teamOverview(team.getId());
                TeamMaterialRowVO row = overview.getData();
                if (row == null || row.getCompleteRate() == null || row.getCompleteRate() >= 100) {
                    continue;
                }
                ObjectNode item = objectMapper.createObjectNode();
                item.put("teamId", team.getId());
                item.put("teamName", team.getName());
                item.put("leaderName", team.getLeaderName());
                item.put("completeRate", row.getCompleteRate());
                item.put("statusText", row.getStatusText());
                item.set("items", objectMapper.valueToTree(row.getItems()));
                missing.add(item);
            }
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.set("missingTeams", missing);
        result.put("total", missing.size());
        return objectMapper.writeValueAsString(result);
    }
}
