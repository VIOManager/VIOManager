package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.project.dto.ProjectQueryDTO;
import com.viomanager.project.service.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：项目列表（清单 §2 补充，管理角色——项目管理为管理端菜单口径）。
 * 复用项目管理分页查询，可按关键词/状态筛选。
 */
@Component
public class QueryProjectsTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ProjectService projectService;

    @Override
    public String name() {
        return "queryProjects";
    }

    @Override
    public String description() {
        return "分页查询平台项目列表（项目名、状态、所属比赛/学期/组织），可按关键词与状态筛选；"
                + "仅管理角色可用；问\"有哪些项目在进行\"时用本工具";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "keyword":{"type":"string","description":"项目名关键词，可选"},
                  "status":{"type":"string","description":"项目状态，可选"},
                  "page":{"type":"integer","description":"页码，默认 1"},
                  "size":{"type":"integer","description":"每页条数，默认 20，最大 50"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        if (!AiToolRoles.isAdmin(user)) {
            return AiToolRoles.DENIED_JSON;
        }
        ProjectQueryDTO dto = new ProjectQueryDTO();
        if (args.path("keyword").isTextual()) {
            dto.setKeyword(args.path("keyword").asText());
        }
        if (args.path("status").isTextual()) {
            dto.setStatus(args.path("status").asText());
        }
        dto.setPage(Math.max(args.path("page").asInt(1), 1));
        dto.setSize(Math.min(Math.max(args.path("size").asInt(20), 1), 50));
        return objectMapper.writeValueAsString(projectService.list(dto));
    }
}
