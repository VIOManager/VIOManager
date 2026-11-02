package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.material.service.MaterialCompletionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：我的材料进度（清单 §2，仅本人域）——学生问"我还有什么材料没交"的正规入口。
 * 复用 C 线 MS06（MaterialCompletionService.myMaterials）：本人每支在册队伍的材料任务、
 * 齐全率、必传缺项与逐项状态，与"我的材料"页面数据完全同源。
 * 此前该问法会掉进死区（queryTeamsMissingMaterials 仅管理角色，模型无路可走念工具名）。
 */
@Component
public class QueryMyMaterialsTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MaterialCompletionService completionService;

    @Override
    public String name() {
        return "queryMyMaterials";
    }

    @Override
    public String description() {
        return "查询当前登录用户本人所在每支赛队的材料提交进度：材料任务与截止时间、齐全率、"
                + "必传还缺几项、逐项材料状态；问\"我还有什么材料没交\"\"我的材料交齐了吗\""
                + "\"未交材料\"时用本工具，仅限本人（查全平台未交齐的赛队是管理员用的另一工具）";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        return objectMapper.writeValueAsString(completionService.myMaterials());
    }
}
