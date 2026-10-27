package com.viomanager.ai.service;

import com.viomanager.ai.support.RagPgClient;
import com.viomanager.common.enums.AiErrorCode;
import com.viomanager.common.exception.BizException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * RAG 检索服务（方案拆步 3）：问题 → 双路召回 → RRF 融合。
 * 向量路 = Spring AI similaritySearch（内嵌问题向量化，兜同义改写与模糊语义）；
 * 关键词路 = 自写 shingle 子串 ILIKE（pg_trgm 索引，兜专有名词精确命中）——
 * Spring AI 只有向量路（方案 §七"知道框架边界在哪"的卖点），关键词路与融合是自己的。
 * RRF：score = Σ 1/(60 + rank)，无参融合不引入 rerank 推理成本。
 * 阈值 0.65：实测 nomic 中文噪声地板约 0.58、原文块约 0.90，0.65 抑噪仍留召回余量。
 * RAG 未启用（零装配）抛 9105，由 AiChatFlow 兜底转错误 JSON 给模型转述，不炸 SSE 流。
 */
@Service
public class RagSearchService {

    private static final double VECTOR_THRESHOLD = 0.65;
    private static final int RRF_K = 60;
    private static final int MAX_SHINGLES = 12;
    private static final Pattern WORD_RUN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]+");

    /**
     * 疑问/功能词先剔除再切 shingle，避免"怎么""哪里"这类高频字面命中带偏关键词路。
     * 长词在前（contains 式剔除，"怎么样"须先于"怎么"）；剔除以分隔符置换防止两词粘成假词。
     */
    private static final List<String> STOP_WORDS = List.of(
            "怎么样", "什么样", "怎样", "怎么", "如何", "什么", "哪些", "哪个", "哪里", "哪儿", "在哪", "多少",
            "吗", "呢", "吧", "啊", "呀", "嘛", "么",
            "请问", "请教", "一下", "帮我", "帮忙", "告诉我", "告诉", "我想", "我要",
            "是不是", "有没有", "能不能", "可以吗", "可以", "应该", "需要", "想要",
            "时候", "地方", "东西", "问题",
            "去", "做", "看", "查", "找", "算", "办", "搞",
            "我们", "你们", "他们", "我", "你", "您", "他", "她", "它",
            "的", "了", "是", "在", "有", "个", "和", "与", "对", "被", "把",
            "能", "会", "该", "要", "就", "都", "也", "还", "又", "再");

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    private final ObjectProvider<RagPgClient> pgClientProvider;

    public RagSearchService(ObjectProvider<VectorStore> vectorStoreProvider,
                            ObjectProvider<RagPgClient> pgClientProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.pgClientProvider = pgClientProvider;
    }

    /**
     * 检索入口（searchKb 工具消费）：topK 上限内融合两路，
     * 命中片段带 docId/docTitle/chunkIndex 出处结构。空问题直接空列表。
     */
    public List<RagHit> search(String question, int topK) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        List<Document> vecHits = requireStore().similaritySearch(SearchRequest.builder()
                .query(q).topK(topK).similarityThreshold(VECTOR_THRESHOLD).build());
        List<String> shingles = extractShingles(q);
        List<RagPgClient.KwRow> kwHits = shingles.isEmpty() ? List.of()
                : requireClient().keywordSearch(shingles, topK);

        Map<String, Merged> merged = new LinkedHashMap<>();
        for (int i = 0; i < vecHits.size(); i++) {
            mergeVector(merged, vecHits.get(i), i);
        }
        for (int i = 0; i < kwHits.size(); i++) {
            mergeKeyword(merged, kwHits.get(i), i);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble((Merged m) -> m.score).reversed())
                .limit(topK)
                .map(m -> new RagHit(m.docId, m.docTitle, m.chunkIndex, m.content,
                        Math.round(m.score * 1e4) / 1e4, List.copyOf(m.sources)))
                .toList();
    }

    /** 检索结果单元：出处字段是"带文档名回答"的数据来源，sources 记录融合前的命中路径 */
    public record RagHit(String docId, String docTitle, int chunkIndex, String content,
                         double score, List<String> sources) {
    }

    private void mergeVector(Map<String, Merged> merged, Document doc, int rank) {
        Map<String, Object> md = doc.getMetadata();
        Merged m = merged.computeIfAbsent(doc.getId(), id -> new Merged(
                str(md.get("docId")), str(md.get("docTitle")),
                intVal(md.get("chunkIndex")), doc.getText()));
        m.score += 1.0 / (RRF_K + rank + 1);
        m.sources.add("vector");
    }

    private void mergeKeyword(Map<String, Merged> merged, RagPgClient.KwRow row, int rank) {
        Merged m = merged.computeIfAbsent(row.id(), id -> new Merged(
                row.docId(), row.docTitle() == null ? "" : row.docTitle(),
                row.chunkIndex(), row.content()));
        m.score += 1.0 / (RRF_K + rank + 1);
        m.sources.add("keyword");
    }

    /**
     * 关键词路的中文 shingle 抽取：疑问词剔除 → 连续汉字/字母数字串 →
     * 短串（2-4 字）整体即术语（"队伍""B305"），长串（≥5 字）滑窗 3-gram
     * （"蓝桥杯比赛报名"→蓝桥杯/桥杯比/…），单字丢弃（噪声）。上限 12 个。
     */
    private List<String> extractShingles(String question) {
        String stripped = question;
        for (String w : STOP_WORDS) {
            stripped = stripped.replace(w, "|");
        }
        Set<String> shingles = new LinkedHashSet<>();
        Matcher matcher = WORD_RUN.matcher(stripped);
        while (matcher.find() && shingles.size() < MAX_SHINGLES) {
            collectRun(matcher.group(), shingles);
        }
        return List.copyOf(shingles);
    }

    private void collectRun(String run, Set<String> out) {
        if (run.length() < 2) {
            return;
        }
        if (run.length() <= 4) {
            out.add(run);
            return;
        }
        for (int i = 0; i + 3 <= run.length() && out.size() < MAX_SHINGLES; i++) {
            out.add(run.substring(i, i + 3));
        }
    }

    private VectorStore requireStore() {
        VectorStore store = vectorStoreProvider.getIfAvailable();
        if (store == null) {
            throw new BizException(AiErrorCode.AI_RAG_NOT_ENABLED);
        }
        return store;
    }

    private RagPgClient requireClient() {
        RagPgClient client = pgClientProvider.getIfAvailable();
        if (client == null) {
            throw new BizException(AiErrorCode.AI_RAG_NOT_ENABLED);
        }
        return client;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intVal(Object value) {
        return value instanceof Number n ? n.intValue() : -1;
    }

    /** RRF 融合中间态：双路按 chunk id 汇聚，score 累加、来源并集 */
    private static final class Merged {
        final String docId;
        final String docTitle;
        final int chunkIndex;
        final String content;
        double score;
        final Set<String> sources = new LinkedHashSet<>(2);

        Merged(String docId, String docTitle, int chunkIndex, String content) {
            this.docId = docId;
            this.docTitle = docTitle;
            this.chunkIndex = chunkIndex;
            this.content = content;
        }
    }
}
