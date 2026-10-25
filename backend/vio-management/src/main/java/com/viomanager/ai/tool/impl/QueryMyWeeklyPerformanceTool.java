package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.performance.service.PerformanceWeeklyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：本周绩效（清单 §2，仅本人域）——本人周绩效/周报，复用 PF 线 myWeeklies。
 */
@Component
public class QueryMyWeeklyPerformanceTool implements AiTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private PerformanceWeeklyService weeklyService;

    @Override
    public String name() {
        return "queryMyWeeklyPerformance";
    }

    @Override
    public String description() {
        return "查询当前登录用户本人的周绩效（周报）列表，可按学期 ID 与状态过滤；"
                + "不传参数返回全部";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "semesterId":{"type":"integer","description":"学期 ID，可选"},
                  "status":{"type":"string","description":"状态筛选（如 SUBMITTED、SCORED），可选"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        Long semesterId = args.path("semesterId").isNumber() ? args.path("semesterId").asLong() : null;
        String status = args.path("status").isTextual() ? args.path("status").asText() : null;
        return MAPPER.writeValueAsString(weeklyService.myWeeklies(semesterId, status));
    }
}
