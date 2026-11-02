package com.viomanager.ai;

import com.viomanager.ai.service.RagIndexService;
import com.viomanager.ai.service.RagSearchService;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * RAG 检索 service 测试（方案拆步 3 的验证闸门）：双路召回 + RRF 融合。
 * 环境前置与 RagVectorStoreTest 同款：postgres 容器在跑（宿主 5433，已带 pg_trgm 扩展
 * 与 gin_trgm_ops 索引）+ 本地 Ollama（nomic-embed-text）在跑。
 * 语料自种自清（docId 97100x 段，@AfterEach 清索引），断言只要求"我的文档在结果里"，
 * 不排除库里其他残留块——抗活库。
 */
@SpringBootTest(properties = {
        "vio.rag.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
class RagSearchServiceTest {

    private static final String TEAM_TITLE = "平台使用指南-组队篇";
    private static final String MATERIAL_TITLE = "平台使用指南-材料篇";
    private static final long TEAM_DOC = 971_001L;
    private static final long MATERIAL_DOC = 971_002L;

    @Resource
    private RagIndexService indexService;

    @Resource
    private RagSearchService searchService;

    @BeforeEach
    void seedCorpus() {
        indexService.syncDocument(TEAM_DOC, TEAM_TITLE, """
                # 组队流程
                想参加比赛的同学先组建赛队：队长进入赛队页面点击创建赛队按钮，填写队名与参赛信息即完成建队。
                建队后可在成员管理页添加队伍成员，系统会向对方发送确认通知，本人确认后正式入队。
                每支队伍有人数上限，超出上限无法继续加队伍成员，报名前请提前规划阵容。
                """);
        indexService.syncDocument(MATERIAL_DOC, MATERIAL_TITLE, """
                # 材料提交
                报名材料统一在材料管理页上传，比赛报名表与队伍合照缺一不可。
                材料初审由协会管理员在科创楼B305办公室完成，纸质版受理时间为每周三下午。
                """);
    }

    @AfterEach
    void cleanCorpus() {
        indexService.removeDocument(TEAM_DOC);
        indexService.removeDocument(MATERIAL_DOC);
    }

    @Test
    void usageQuestionShouldHitTeamGuide() {
        List<RagSearchService.RagHit> hits = searchService.search("怎么加队伍", 5);
        Assertions.assertFalse(hits.isEmpty(), "使用类问题应召回片段");
        Assertions.assertTrue(hits.size() <= 5, "topK 上限应被尊重");
        Assertions.assertTrue(hits.stream().anyMatch(h -> TEAM_TITLE.equals(h.docTitle())),
                "top5 应含组队文档块：" + hits.stream().map(RagSearchService.RagHit::docTitle).toList());
        Assertions.assertTrue(hits.stream().anyMatch(h -> h.sources().contains("vector")),
                "向量路应参与召回（nomic 语义命中阈值 0.65）");
    }

    @Test
    void keywordPathShouldRescueExactTerm() {
        //纯专有编码短问句：向量路对"B305"这类短编码可能不足阈值，关键词路 shingle ILIKE 兜精确命中——
        //这正是双路存在的理由（方案 §七：单路各有盲区）
        List<RagSearchService.RagHit> hits = searchService.search("B305", 5);
        Assertions.assertTrue(hits.stream().anyMatch(h -> MATERIAL_TITLE.equals(h.docTitle())),
                "精确术语应由关键词路召回：" + hits);
    }

    @Test
    void fusedHitsShouldCarryCitationFields() {
        List<RagSearchService.RagHit> hits = searchService.search("报名材料在哪交", 5);
        Assertions.assertFalse(hits.isEmpty(), "材料类问句应召回片段");
        for (RagSearchService.RagHit h : hits) {
            Assertions.assertNotNull(h.docTitle(), "出处字段应随行带回（带文档名回答靠它）");
            Assertions.assertFalse(h.content().isBlank(), "片段内容非空");
            Assertions.assertFalse(h.sources().isEmpty(), "来源标记非空");
            Assertions.assertTrue(h.score() > 0, "RRF 分数应为正");
        }
    }

    @Test
    void blankQuestionShouldReturnEmpty() {
        Assertions.assertTrue(searchService.search("   ", 5).isEmpty(),
                "空问题应返回空列表而非报错（模型传空参数时不炸流）");
    }
}
