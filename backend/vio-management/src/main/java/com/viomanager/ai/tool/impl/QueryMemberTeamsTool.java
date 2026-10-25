package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonVO;
import com.viomanager.person.service.PersonService;
import com.viomanager.team.service.ITeamQueryService;
import com.viomanager.team.vo.TeamDetailVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 白名单工具：成员参赛查询（清单 §2 补充，管理角色）——"他/她/某某人参加过什么比赛、在哪个队"。
 * 链路：姓名/学号 → A 线人员分页拿 memberId → C 线跨线支撑 MaterialTeamQuerySupport 查在册赛队
 * → B 线 detail 补比赛名。组长 2026-10-25 实测痛点：模型没有此域工具时错拿
 * queryMyAchievements 查他人，答出"您没有参加过比赛"的指代错乱。
 */
@Component
public class QueryMemberTeamsTool implements AiTool {

    /** 单人队伍数上限：防极端脏数据把上下文撑爆，正常学生 1-3 队 */
    private static final int MAX_TEAMS = 10;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PersonService personService;

    @Resource
    private MaterialTeamQuerySupport materialTeamQuerySupport;

    @Resource
    private ITeamQueryService teamQueryService;

    @Override
    public String name() {
        return "queryMemberTeams";
    }

    @Override
    public String description() {
        return "按姓名或学号查询某个成员参加的赛队与比赛（含队长身份与队伍状态）；"
                + "问\"他/她/某人参加过什么比赛、在哪个队\"时用本工具，仅管理角色可用";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "keyword":{"type":"string","description":"成员姓名或学号"},
                  "memberId":{"type":"integer","description":"成员 ID（人员档案 id），已知时可直传"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        if (!AiToolRoles.isAdmin(user)) {
            return AiToolRoles.DENIED_JSON;
        }
        Long memberId = args.path("memberId").isNumber() ? args.path("memberId").asLong() : null;
        String memberName = null;
        ObjectNode root = objectMapper.createObjectNode();
        if (memberId == null) {
            String keyword = args.path("keyword").isTextual() ? args.path("keyword").asText() : null;
            if (keyword == null || keyword.isBlank()) {
                return "{\"error\":\"请提供成员姓名或学号\"}";
            }
            PersonQueryDTO dto = new PersonQueryDTO();
            dto.setKeyword(keyword);
            dto.setPage(1);
            dto.setSize(5);
            Result<PageResult<PersonVO>> page = personService.page(dto);
            List<PersonVO> matched = page.getData() == null ? null : page.getData().getList();
            if (matched == null || matched.isEmpty()) {
                return "{\"error\":\"未找到该人员，请确认姓名或学号\"}";
            }
            PersonVO person = matched.get(0);
            memberId = person.getId();
            memberName = person.getName();
            root.put("matchedCount", matched.size());
            if (matched.size() > 1) {
                ArrayNode others = root.putArray("alsoMatched");
                for (int i = 1; i < matched.size(); i++) {
                    others.add(matched.get(i).getName() + "(" + matched.get(i).getStudentNo() + ")");
                }
            }
        }
        List<MaterialTeamQuerySupport.TeamMembership> memberships =
                materialTeamQuerySupport.loadMyTeamMemberships(memberId);
        ObjectNode member = root.putObject("member");
        member.put("id", memberId);
        if (memberName != null) {
            member.put("name", memberName);
        }
        ArrayNode teams = root.putArray("teams");
        int limit = Math.min(memberships.size(), MAX_TEAMS);
        for (int i = 0; i < limit; i++) {
            MaterialTeamQuerySupport.TeamMembership membership = memberships.get(i);
            ObjectNode team = teams.addObject();
            team.put("teamId", membership.getTeamId());
            team.put("teamName", membership.getTeamName());
            team.put("isLeader", membership.isLeader());
            team.put("teamStatus", membership.getTeamStatus());
            Result<TeamDetailVO> detail = teamQueryService.detail(membership.getTeamId());
            if (detail.getData() != null) {
                TeamDetailVO vo = detail.getData();
                team.put("contestName", vo.getContestName());
                team.put("projectName", vo.getProjectName());
            }
        }
        root.put("teamCount", memberships.size());
        return objectMapper.writeValueAsString(root);
    }
}
