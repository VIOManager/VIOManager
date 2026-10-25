package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.schedule.service.ScheduleService;
import com.viomanager.schedule.support.SemesterSupport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：排班总表（清单 §2 补充，管理角色——排班管理为管理端菜单，SC 口径同 queryFreeSlots）。
 * 复用 S 线 weekTable：某周全组织（或指定组织）的值班安排矩阵。
 * semesterId 空自动解析当前学期；weekNo 必须给（不知道周次可先调 querySemesterInfo）。
 */
@Component
public class QueryScheduleTableTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ScheduleService scheduleService;

    @Resource
    private SemesterSupport semesterSupport;

    @Override
    public String name() {
        return "queryScheduleTable";
    }

    @Override
    public String description() {
        return "查询某一周的排班总表（各组织谁在值班/值日）；仅管理角色可用；"
                + "需要 weekNo 周次（不知道当前第几周可先调 querySemesterInfo），"
                + "semesterId 不传默认当前学期，subOrgId 可选指定具体组织";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "semesterId":{"type":"integer","description":"学期 ID，不传默认当前学期"},
                  "weekNo":{"type":"integer","description":"周次（第几周）"},
                  "subOrgId":{"type":"integer","description":"组织 ID，可选"}
                },"required":["weekNo"]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        if (!AiToolRoles.isAdmin(user)) {
            return AiToolRoles.DENIED_JSON;
        }
        Long semesterId = args.path("semesterId").isNumber() ? args.path("semesterId").asLong() : null;
        if (semesterId == null) {
            SemesterSupport.SemesterRow current = semesterSupport.currentActive();
            if (current == null) {
                return "{\"error\":\"当前没有激活学期，请先在学期管理中激活学期\"}";
            }
            semesterId = current.id();
        }
        Integer weekNo = args.path("weekNo").isNumber() ? args.path("weekNo").asInt() : null;
        Long subOrgId = args.path("subOrgId").isNumber() ? args.path("subOrgId").asLong() : null;
        ObjectNode root = objectMapper.createObjectNode();
        root.put("semesterId", semesterId);
        root.put("weekNo", weekNo);
        root.set("weekTable", objectMapper.valueToTree(
                scheduleService.weekTable(semesterId, weekNo, subOrgId)));
        return objectMapper.writeValueAsString(root);
    }
}
