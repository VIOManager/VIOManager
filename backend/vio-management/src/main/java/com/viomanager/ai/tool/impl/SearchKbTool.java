package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.service.RagSearchService;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 白名单工具：知识库检索（清单 §2 第 16 个，RAG 拆步 4）——平台使用类问题的入口，
 * 与 15 个活数据查询工具分工：数据类问题走 query*，"怎么操作/流程/在哪"走本工具。
 * 登录即可（知识库指南全员适用）；RAG 未启用时 RagSearchService 抛 9105，
 * 由 AiChatFlow 兜底转 {"error":...} 给模型转述，不炸 SSE 流。
 * 返回片段带 docTitle 出处，note 要求模型注明文档名且检索不到时如实说明——防编造。
 */
@Component
public class SearchKbTool implements AiTool {

    private static final int TOP_K = 5;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RagSearchService ragSearchService;

    @Override
    public String name() {
        return "searchKb";
    }

    @Override
    public String description() {
        return "检索平台知识库文档，回答平台使用与流程类问题（怎么创建赛队、材料在哪提交、"
                + "怎么报名比赛、材料初审在哪办公等）时优先调用；返回带文档标题出处的片段，"
                + "依据片段回答并注明出自哪篇文档；查某人/某队/排班等实时数据不要用本工具";
    }

    @Override
    public String parametersSchema() {
        return """
                {"type":"object","properties":{
                  "question":{"type":"string","description":"要检索的问题或关键词，直接传用户原话"}
                },"required":["question"]}""";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        String question = args.path("question").asText("");
        List<RagSearchService.RagHit> hits = ragSearchService.search(question, TOP_K);
        if (hits.isEmpty()) {
            return objectMapper.writeValueAsString(Map.of(
                    "total", 0, "hits", List.of(),
                    "note", "知识库未检索到相关内容，请如实告知用户，不要编造流程或答案"));
        }
        return objectMapper.writeValueAsString(Map.of(
                "total", hits.size(),
                "hits", hits,
                "note", "依据以上片段回答并注明出自哪篇文档；片段不足以回答时如实说明知识库未覆盖"));
    }
}
