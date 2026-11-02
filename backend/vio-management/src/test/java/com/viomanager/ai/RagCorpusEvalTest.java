package com.viomanager.ai;

import com.viomanager.ai.service.RagIndexService;
import com.viomanager.ai.service.RagSearchService;
import com.viomanager.ai.service.RagSearchService.RagHit;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * RAG 语料评估闸门（拆步 5）：docs/kb-corpus 的 15 篇平台使用指南真实切片入库后，
 * 18 个自然语言问句逐条断言期望文档进检索 top5——语料写坏了（用户问法检索不中）这里先红。
 * docId 用 972xxx 自种段，与 kb 种子 960xxx、RagIndexFlowTest 970001 互不纠缠。
 * 环境前置同 RagVectorStoreTest：postgres 容器（5433）+ 本地 Ollama 在跑。
 */
@SpringBootTest(properties = {
        "vio.rag.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagCorpusEvalTest {

    private static final Path CORPUS_DIR = Path.of("..", "..", "docs", "kb-corpus");
    private static final long DOC_ID_BASE = 972_000L;
    private static final int TOP_K = 5;

    /** 文件名 → 该篇要接住的用户问句（口语化，不抄文档原句，检验的是检索不是背书） */
    private static final List<Case> CASES = List.of(
            new Case("01-入门与账号篇.md", "第一次登录的初始密码是什么"),
            new Case("01-入门与账号篇.md", "密码输错被锁定了怎么办"),
            new Case("02-人员档案与权限篇.md", "为什么我看不到别人的手机号"),
            new Case("03-人员导入与批量管理篇.md", "批量导入人员的规则是什么"),
            new Case("04-组织与部门管理篇.md", "部门管理员能管哪些人"),
            new Case("05-学期与切换篇.md", "学期切换的时候我的队伍会怎么样"),
            new Case("06-组队篇.md", "怎么创建队伍"),
            new Case("06-组队篇.md", "队伍预填成员最多能填几个人"),
            new Case("07-比赛篇.md", "比赛都有哪几种状态"),
            new Case("08-材料篇.md", "材料过了截止时间还能交吗"),
            new Case("08-材料篇.md", "怎么用压缩包一次交多份材料"),
            new Case("09-成果篇.md", "成果申报的流程是什么"),
            new Case("10-项目篇.md", "怎么把项目挂载到赛队"),
            new Case("11-排班与课表篇.md", "班级映射为什么必须确认"),
            new Case("12-绩效周报篇.md", "周报一周能交几份"),
            new Case("13-AI助手使用篇.md", "AI 助手每分钟最多能问几次"),
            new Case("14-AI知识问答篇.md", "什么样的平台使用问题适合问 AI"),
            new Case("15-知识库篇.md", "文档发布后 AI 能引用到它吗"));

    private record Case(String file, String question) {}

    @Resource
    private RagIndexService indexService;

    @Resource
    private RagSearchService searchService;

    /** 文件名 → docId 与标题（标题取 md 首行 H1，单一正源防两处写死走偏） */
    private final Map<String, long[]> seeded = new TreeMap<>();
    private final Map<String, String> titles = new TreeMap<>();

    @BeforeAll
    void seedCorpus() throws IOException {
        Assertions.assertTrue(Files.isDirectory(CORPUS_DIR),
                "语料目录不存在: " + CORPUS_DIR.toAbsolutePath() + "（先落 docs/kb-corpus）");
        List<Path> files = Files.list(CORPUS_DIR)
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .sorted()
                .toList();
        Assertions.assertTrue(files.size() >= 12, "语料至少 12 篇口径，实际 " + files.size());
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String text = Files.readString(file).strip();
            String title = text.split("\n", 2)[0].replaceFirst("^# ", "").strip();
            long docId = DOC_ID_BASE + i + 1;
            indexService.syncDocument(docId, title, text);
            seeded.put(file.getFileName().toString(), new long[] {docId});
            titles.put(file.getFileName().toString(), title);
        }
    }

    @AfterAll
    void cleanCorpus() {
        seeded.values().forEach(id -> indexService.removeDocument(id[0]));
    }

    @Test
    void everyQuestionShouldHitItsDocInTop5() {
        List<String> failures = new ArrayList<>();
        for (Case c : CASES) {
            long expectedId = seeded.get(c.file())[0];
            String expectedTitle = titles.get(c.file());
            List<RagHit> hits = searchService.search(c.question(), TOP_K);
            boolean hit = hits.stream().anyMatch(h -> String.valueOf(expectedId).equals(h.docId()));
            if (!hit) {
                failures.add("问句「%s」期望《%s》未进 top5，实际 top3: %s".formatted(
                        c.question(), expectedTitle, summarize(hits)));
            }
        }
        Assertions.assertTrue(failures.isEmpty(),
                "语料检索评估未过（%d/%d 失败）:\n".formatted(failures.size(), CASES.size())
                        + String.join("\n", failures));
    }

    private String summarize(List<RagHit> hits) {
        if (hits.isEmpty()) {
            return "（空）";
        }
        return hits.stream().limit(3)
                .map(h -> h.docTitle() + "#" + h.chunkIndex())
                .reduce((a, b) -> a + " | " + b)
                .orElse("（空）");
    }
}
