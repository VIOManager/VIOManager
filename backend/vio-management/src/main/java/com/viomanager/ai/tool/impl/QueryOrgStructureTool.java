package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.org.service.OrgService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：组织架构（清单 §2 补充，管理角色——组织树接口为管理端口径）。
 * 返回 A 线组织树：学院/部门层级、负责人。问"XX 学院下有哪些部门"时用。
 */
@Component
public class QueryOrgStructureTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private OrgService orgService;

    @Override
    public String name() {
        return "queryOrgStructure";
    }

    @Override
    public String description() {
        return "查询平台组织架构树：学院与下属部门的层级关系及各部门负责人；仅管理角色可用；"
                + "问\"有哪些学院/部门\"\"XX 学院下面有什么\"时用本工具，无需参数";
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
        return objectMapper.writeValueAsString(orgService.tree());
    }
}
