package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.schedule.service.FreeSlotService;
import com.viomanager.schedule.support.SemesterSupport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 白名单工具：空课查询（清单 §2，仅管理角色——PRD §3.9 明确 SC06/SC07 仅超管与部门管理员）。
 * 给了具体星期与节次返回该格空课名单，否则返回整周 7×5 空课概览矩阵；
 * 复用 S 线 FreeSlotService，判定规则同源。
 * FreeSlot 强校验学期：semesterId 空时工具层解析当前激活学期；
 * weekNo 不代填——service 报"周次须在 1–N 内"，模型据此反问用户第几周。
 */
@Component
public class QueryFreeSlotsTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private FreeSlotService freeSlotService;

    @Resource
    private SemesterSupport semesterSupport;

    @Override
    public String name() {
        return "queryFreeSlots";
    }

    @Override
    public String description() {
        return "查询成员空课情况（仅管理角色）：需给 weekNo 周次；再传 weekday（1-7，周一为 1）"
                + "与 slotIndex（1-5，五节大课）返回该时间格有空成员名单，"
                + "不传 weekday 与 slotIndex 则返回该周 7×5 每格空课人数概览；不知道周次时先问用户";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "semesterId":{"type":"integer","description":"学期 ID，不传默认当前学期"},
                  "weekNo":{"type":"integer","description":"周次（第几周），可选"},
                  "weekday":{"type":"integer","description":"星期几（1=周一 … 7=周日），传了连同 slotIndex 返回名单"},
                  "slotIndex":{"type":"integer","description":"第几节大课（1-5）"}
                },"required":[]}""";
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
        Integer weekday = args.path("weekday").isNumber() ? args.path("weekday").asInt() : null;
        Integer slotIndex = args.path("slotIndex").isNumber() ? args.path("slotIndex").asInt() : null;
        ObjectNode result = objectMapper.createObjectNode();
        if (weekday != null && slotIndex != null) {
            result.put("mode", "members");
            result.set("freeMembers", objectMapper.valueToTree(
                    freeSlotService.members(semesterId, weekNo, weekday, slotIndex)));
        } else {
            result.put("mode", "matrix");
            List<?> matrix = freeSlotService.matrix(semesterId, weekNo);
            result.set("weekMatrix", objectMapper.valueToTree(matrix));
        }
        return objectMapper.writeValueAsString(result);
    }
}
