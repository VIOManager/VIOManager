package com.viomanager.kb.support;

import com.viomanager.kb.entity.KbDoc;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * KB → AI 主服务的 RAG 薄通知（拆步 2）：文档发布/编辑/下架后推可检索文本给 AI 索引端点。
 * "薄"的三层含义：KB 侧钩子一行调用；AI 侧负责切片/向量化全部重活；AI 掉线/慢不影响
 * KB 主流程——发布路径走单线程守护队列异步投递，异常全吞只记 warn（漏通知的文档用
 * 管理端 rag-rebuild 一键补齐）。管理端重建走同步版（要成败汇总）。
 */
@Component
public class RagNotifyClient {

    private static final Logger log = LoggerFactory.getLogger(RagNotifyClient.class);

    private final RestTemplate restTemplate;

    private final String indexUrl;

    private final String token;

    /** 发布/编辑/下架钩子的投递队列：单线程守护线程；KB 写频极低，无界队列不会积压 */
    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kb-rag-notify");
        t.setDaemon(true);
        return t;
    });

    public RagNotifyClient(@Value("${vio.rag.index-url}") String indexUrl,
                           @Value("${vio.rag.token}") String token) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(30_000);   // 全文重切含逐块 embedding，秒级/篇
        this.restTemplate = new RestTemplate(factory);
        this.indexUrl = indexUrl;
        this.token = token;
    }

    /** 异步通知：文档发布/编辑后重切索引。失败只记日志，绝不挡 KB 主流程 */
    public void notifyDocAsync(KbDoc doc) {
        long docId = doc.getId();
        String title = doc.getTitle();
        String text = searchableText(doc);
        queue.execute(() -> {
            try {
                send(docId, title, text);
            } catch (Exception e) {
                log.warn("RAG 索引通知失败（docId={}，管理端 rag-rebuild 可补）：{}", docId, e.getMessage());
            }
        });
    }

    /** 异步通知：文档下架/删除清索引（content 传 null 即 AI 侧删除语义） */
    public void notifyRemoveAsync(long docId) {
        queue.execute(() -> {
            try {
                send(docId, null, null);
            } catch (Exception e) {
                log.warn("RAG 清索引通知失败（docId={}）：{}", docId, e.getMessage());
            }
        });
    }

    /** 同步推送（管理端全量重建用）：返回是否成功 */
    public boolean sendDocSync(KbDoc doc) {
        try {
            send(doc.getId(), doc.getTitle(), searchableText(doc));
            return true;
        } catch (Exception e) {
            log.warn("RAG 索引重建推送失败（docId={}）：{}", doc.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 可检索文本组成（embedding 只吃正文，metadata 不参与向量化）：
     * MANUAL=标题+正文；LINK/FILE=标题+摘要（整库可搜的最小代价方案）。
     */
    private String searchableText(KbDoc doc) {
        StringBuilder sb = new StringBuilder(doc.getTitle());
        if (StringUtils.hasText(doc.getContent())) {
            sb.append("\n\n").append(doc.getContent());
        } else if (StringUtils.hasText(doc.getSummary())) {
            sb.append("\n").append(doc.getSummary());
        }
        return sb.toString();
    }

    private void send(long docId, String docTitle, String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-RAG-Token", token);
        Map<String, Object> body = new HashMap<>();
        body.put("docId", docId);
        body.put("docTitle", docTitle);
        body.put("content", content);
        restTemplate.postForEntity(indexUrl, new HttpEntity<>(body, headers), String.class);
    }
}
