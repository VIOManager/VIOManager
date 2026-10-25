package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.result.Result;
import com.viomanager.team.service.ITeamQueryService;
import com.viomanager.team.vo.TeamListItemVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 白名单工具：赛队查询（清单 §2，登录即可调）——按 ID 查单队详情+队员名单，
 * 或按比赛/关键词列队伍。可见性复用 B 线 TE 查询的 service 层三角色分流
 * （超管全部 / 部门管理员按组织子树 / 学生仅本队），工具层不另设权限。
 */
@Component
public class QueryTeamTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ITeamQueryService teamQueryService;

    @Override
    public String name() {
        return "queryTeam";
    }

    @Override
    public String description() {
        return "查询赛队信息：传 teamId 返回该队详情与队员名单；"
                + "不传 teamId 时按 contestId 或 keyword 列出可见的赛队列表"
                + "（keyword 只传纯队名或项目名，不带其他字）";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "teamId":{"type":"integer","description":"赛队 ID，传了则返回该队详情与队员"},
                  "contestId":{"type":"integer","description":"按比赛 ID 筛选赛队列表，可选"},
                  "keyword":{"type":"string","description":"队名或项目名关键词，可选"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        if (args.path("teamId").isNumber()) {
            long teamId = args.path("teamId").asLong();
            ObjectNode combined = objectMapper.createObjectNode();
            combined.set("detail", objectMapper.valueToTree(teamQueryService.detail(teamId)));
            combined.set("members", objectMapper.valueToTree(teamQueryService.listMembers(teamId)));
            return objectMapper.writeValueAsString(combined);
        }
        Long contestId = args.path("contestId").isNumber() ? args.path("contestId").asLong() : null;
        String keyword = args.path("keyword").isTextual() ? args.path("keyword").asText() : null;
        Result<List<TeamListItemVO>> teams = teamQueryService.listTeams(contestId, keyword);
        ObjectNode root = objectMapper.createObjectNode();
        //qwen2.5 会把"星辰算法队的管理"整句塞进 keyword，LIKE 全串匹配不到；
        //关键词没中就退化为全量列表（数据范围不变），且必须带显式 note——
        //否则模型会把回退列表硬贴上用户问的队名（实测学生问他人队被答成本队数据换了标签）
        if (keyword != null && (teams.getData() == null || teams.getData().isEmpty())) {
            teams = teamQueryService.listTeams(contestId, null);
            root.put("note", "关键词\"" + keyword + "\"未匹配到队伍，以下是当前用户数据范围内可见的全部赛队，"
                    + "其中不含所问队伍时应如实回答查不到，不要把列表里的队伍当作所问队伍");
        }
        root.set("teams", objectMapper.valueToTree(teams));
        return objectMapper.writeValueAsString(root);
    }
}
