package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.contest.service.IContestQueryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：比赛列表（清单 §2 补充，登录即可——比赛查询 controller 无角色限制，学生可看）。
 * 复用 B 线比赛查询：按学期/状态列比赛（名称、报名时间、比赛时间、赛道数）。
 */
@Component
public class QueryContestsTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    /** 字段名不能叫 contestQueryService：@Resource 按名先匹配会撞上 team 包同名 Bean */
    @Resource
    private IContestQueryService contestQuery;

    @Override
    public String name() {
        return "queryContests";
    }

    @Override
    public String description() {
        return "查询平台比赛列表：比赛名称、所属学期、状态、报名起止与比赛起止时间；"
                + "可按学期 ID 与状态（如 SIGNUP/ONGOING/FINISHED）筛选；问\"有哪些比赛\"时用本工具";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "semesterId":{"type":"integer","description":"学期 ID，可选"},
                  "status":{"type":"string","description":"比赛状态，可选"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        Long semesterId = args.path("semesterId").isNumber() ? args.path("semesterId").asLong() : null;
        String status = args.path("status").isTextual() ? args.path("status").asText() : null;
        return objectMapper.writeValueAsString(
                contestQuery.listContests(semesterId, status));
    }
}
