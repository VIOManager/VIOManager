package com.viomanager.ai;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * RAG 拆步 1 闸门：PgVectorStore 手动装配的真实往返。
 * 环境前置（与 RocketMQ 测试同款）：compose 的 postgres 容器在跑（宿主映射 5433）、
 * 本地 Ollama 在跑（embedding 真实调用两次：入库一次、查询一次）。
 * 验证四件：Bean 装配上 / 文档带 metadata 出处入库 / 相似度检索召回且回带出处 / 按 id 清理。
 */
@SpringBootTest(properties = {
        "vio.rag.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
class RagVectorStoreTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void addSearchAndDeleteShouldRoundTrip() {
        String marker = "rag-step1-" + System.nanoTime();
        Document doc = new Document(
                "参加比赛前需要先组建赛队：队长进入赛队页面点击创建赛队，填写队名与参赛信息后即完成建队。",
                Map.of("docId", 999, "docTitle", "平台使用指南-组队篇", "chunkIndex", 0, "marker", marker));
        vectorStore.add(List.of(doc));
        try {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query("怎么创建赛队").topK(5).similarityThreshold(0.3).build());
            Document hit = hits.stream()
                    .filter(d -> marker.equals(d.getMetadata().get("marker")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("相似度检索未召回本测试写入的文档，命中数=" + hits.size()));
            Assertions.assertEquals(999, ((Number) hit.getMetadata().get("docId")).intValue(),
                    "召回文档的 metadata 出处必须完整带回（回答带文档标题靠它）");
        } finally {
            vectorStore.delete(List.of(doc.getId()));
        }
    }
}
