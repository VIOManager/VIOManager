package com.viomanager.ai;

import com.viomanager.ai.config.RagProperties;
import com.viomanager.ai.service.RagIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RAG 同步端点流程测试（拆步 2）：令牌闸 + 入库/重切幂等/清索引 + 检索出处回带。
 * 环境前置与 RagVectorStoreTest 同款：postgres 容器（5433）+ 本地 Ollama 在跑。
 */
@SpringBootTest(properties = {
        "vio.rag.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
@AutoConfigureMockMvc
class RagIndexFlowTest {

    private static final long DOC_ID = 970_001L;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private RagIndexService indexService;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private ObjectMapper mapper;

    @AfterEach
    void cleanup() {
        indexService.removeDocument(DOC_ID);
    }

    @Test
    void wrongTokenShouldBeRejected() throws Exception {
        Map<String, Object> body = Map.of("docId", DOC_ID, "docTitle", "指南", "content", "x");
        //无令牌与错令牌都 fail-closed 拒 1002（房子错误码映射：1002 → HTTP 401）
        mockMvc.perform(post("/api/ai/rag/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1002));
        mockMvc.perform(post("/api/ai/rag/sync")
                        .header("X-RAG-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void syncResyncAndRemoveShouldBeIdempotent() throws Exception {
        //1.双节文档入库：两块
        String oldContent = "# 组队流程\n" + "队长在赛队页面点击创建赛队按钮。".repeat(20)
                + "\n# 材料提交\n" + "报名表和合照在材料页上传提交。".repeat(20);
        sync(Map.of("docId", DOC_ID, "docTitle", "平台使用指南-组队篇", "content", oldContent))
                .andExpect(jsonPath("$.data.chunks").value(2));
        Assertions.assertEquals(2, myChunks().size(), "按 docId 过滤应数出 2 块");
        //2.检索召回且出处回带（docId 字符串口径）
        Assertions.assertTrue(hitMine(search("怎么创建赛队", 0.3)), "新入库文档应被检索召回");
        //3.重切：同 docId 换单节内容 → 1 块。块数走 docId 过滤的确定性断言；
        //  语义负向断言用高阈值——实测 nomic 对中文噪声地板 ~0.58、原文块 ~0.90，0.75 居中留余量
        String newContent = "# 退赛说明\n队长可以在赛队页面申请退赛。";
        sync(Map.of("docId", DOC_ID, "docTitle", "平台使用指南-退赛篇", "content", newContent))
                .andExpect(jsonPath("$.data.chunks").value(1));
        Assertions.assertEquals(1, myChunks().size(), "旧块应被删除（文档更新即重切），本文档只剩新块");
        Assertions.assertFalse(hitMine(search("报名表和合照在材料页怎么上传", 0.75)),
                "旧内容高相似命中不应残留");
        //4.清索引：content 空 → removed；之后块数为 0
        Map<String, Object> remove = new HashMap<>();
        remove.put("docId", DOC_ID);
        sync(remove).andExpect(jsonPath("$.data.removed").value(true));
        Assertions.assertEquals(0, myChunks().size(), "清索引后本文档应无块");
        Assertions.assertFalse(hitMine(search("怎么退赛", 0.3)), "清索引后不应再命中");
    }

    private ResultActions sync(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/ai/rag/sync")
                        .header("X-RAG-Token", ragProperties.internalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private List<Document> search(String query, double threshold) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(5).similarityThreshold(threshold).build());
    }

    /** 按 docId 过滤数本文档的块数（thresholdAll 不过滤相似度）——删除语义的确定性断言 */
    private List<Document> myChunks() {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query("块数统计").topK(50).similarityThresholdAll()
                .filterExpression("docId == '" + DOC_ID + "'").build());
    }

    private boolean hitMine(List<Document> hits) {
        return hits.stream()
                .anyMatch(d -> String.valueOf(DOC_ID).equals(d.getMetadata().get("docId")));
    }
}
