package com.viomanager.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * RAG Step 0 版本闸门：Spring AI 1.0.0 × Boot 3.5.5 非官方配对的真实往返验证。
 * 断言 768 维 = nomic-embed-text 模型正常出向量，覆盖自动装配 → Bean 创建 → 本地 Ollama 全链路。
 */
@SpringBootTest
class EmbeddingRoundtripTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void embedShouldReturn768Dims() {
        Assertions.assertEquals(768, embeddingModel.embed("怎么加队伍").length);

    }
}
