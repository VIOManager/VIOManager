package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.service.PersonService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：人员档案（清单 §2，管理角色）——按关键词/成员类型分页查成员基本信息，
 * 复用 A 线 M01 分页查询，数据范围与本人直调一致（部门管理员按组织子树裁剪）。
 */
@Component
public class QueryMembersTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PersonService personService;

    @Override
    public String name() {
        return "queryMembers";
    }

    @Override
    public String description() {
        return "按关键词（姓名/学号）或成员类型分页查询平台人员档案基本信息；仅管理角色可用";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "keyword":{"type":"string","description":"姓名或学号关键词，可选"},
                  "memberType":{"type":"string","description":"成员类型筛选，可选"},
                  "page":{"type":"integer","description":"页码，默认 1"},
                  "size":{"type":"integer","description":"每页条数，默认 20，最大 50"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        if (!AiToolRoles.isAdmin(user)) {
            return AiToolRoles.DENIED_JSON;
        }
        PersonQueryDTO dto = new PersonQueryDTO();
        if (args.path("keyword").isTextual()) {
            dto.setKeyword(args.path("keyword").asText());
        }
        if (args.path("memberType").isTextual()) {
            dto.setMemberType(args.path("memberType").asText());
        }
        dto.setPage(Math.max(args.path("page").asInt(1), 1));
        dto.setSize(Math.min(Math.max(args.path("size").asInt(20), 1), 50));
        return objectMapper.writeValueAsString(personService.page(dto));
    }
}
