package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.approval.dto.ApprovalQueryDTO;
import com.viomanager.approval.service.IApprovalQueryService;
import com.viomanager.common.auth.CurrentUser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：我的审批（清单 §2 补充，登录即可——本人域）。
 * 复用审批中心 P01 三视角列表：管理角色默认 TODO（我的待办），学生默认 MINE（我发起的）；
 * TODO 视角 service 层对学生直接 403，故学生绝不传 TODO。
 */
@Component
public class QueryMyApprovalsTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private IApprovalQueryService approvalQueryService;

    @Override
    public String name() {
        return "queryMyApprovals";
    }

    @Override
    public String description() {
        return "查询与当前登录用户相关的审批单：管理角色默认返回待我处理的待办（view=TODO），"
                + "学生默认返回我发起的申请（view=MINE）；view 可传 MINE（我发起的）或 TARGET（我是目标人），"
                + "可按状态与关键词筛选；问\"我有哪些待办审批\"\"我发起的审批进展\"时用本工具";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "view":{"type":"string","description":"视角：MINE 我发起的 / TARGET 我是目标人；学生不可用 TODO"},
                  "status":{"type":"string","description":"审批状态，可选"},
                  "keyword":{"type":"string","description":"关键词，可选"}
                },"required":[]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        ApprovalQueryDTO dto = new ApprovalQueryDTO();
        String view = args.path("view").isTextual() ? args.path("view").asText() : null;
        if (view == null || view.isBlank()) {
            view = AiToolRoles.isAdmin(user) ? "TODO" : "MINE";
        }
        if ("TODO".equals(view) && !AiToolRoles.isAdmin(user)) {
            //service 层对 TODO 视角学生也是 403，工具层提前分流免炸
            view = "MINE";
        }
        dto.setView(view);
        if (args.path("status").isTextual()) {
            dto.setStatus(args.path("status").asText());
        }
        if (args.path("keyword").isTextual()) {
            dto.setKeyword(args.path("keyword").asText());
        }
        dto.setPage(1);
        dto.setSize(20);
        return objectMapper.writeValueAsString(approvalQueryService.list(dto));
    }
}
