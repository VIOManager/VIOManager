package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.schedule.service.ScheduleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：我的排班（清单 §2，仅本人域）——复用 S 线 my 查询，数据范围与本人直调一致。
 */
@Component
public class QueryMyScheduleTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ScheduleService scheduleService;

    @Override
    public String name() {
        return "queryMySchedule";
    }

    @Override
    public String description() {
        return "查询当前登录用户本人的排班（值班/值日）列表，可按学期 ID 与周次过滤；"
                + "不传参数则返回全部学期的排班";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "semesterId":{"type":"integer","description":"学期 ID，可选"},
                  "weekNo":{"type":"integer","description":"周次（第几周），可选"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        Long semesterId = args.path("semesterId").isNumber() ? args.path("semesterId").asLong() : null;
        Integer weekNo = args.path("weekNo").isNumber() ? args.path("weekNo").asInt() : null;
        return objectMapper.writeValueAsString(scheduleService.my(semesterId, weekNo));
    }
}
