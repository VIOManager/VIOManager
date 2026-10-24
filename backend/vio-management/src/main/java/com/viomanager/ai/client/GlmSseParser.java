package com.viomanager.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

/**
 * OpenAI 兼容 SSE 流解析器（云端智谱 / 本地 Ollama 同一口子）。
 * 逐行喂上游原始行（含 "data:" 前缀），返回 GlmStreamEvent；
 * finish_reason 与 usage 可能分落在不同 chunk，parser 内部聚合，
 * [DONE] 行触发流终止事件，之后由调用方读 getFinishReason / token 数。
 * 非线程安全：一条上游流一个实例。
 * 解析失败的行静默忽略（上游加字段/心跳行不炸流），只记不了日志——技术层兜底口径。
 */
@Getter
public class GlmSseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 聚合的结束原因：stop 正常 / length 截断 / tool_calls 要工具（Step6） */
    private String finishReason;

    /** 聚合的 usage（stream_options.include_usage 的收尾 chunk 才带，可能始终没有） */
    private Long promptTokens;
    private Long completionTokens;

    /**
     * 消费一行上游 SSE 输出；返回 null 表示忽略（空行/注释/无内容的 chunk）。
     */
    public GlmStreamEvent accept(String line) {
        if (line == null || line.isBlank() || line.startsWith(":")) {
            return null;
        }
        if (!line.startsWith("data:")) {
            return null;
        }
        String payload = line.substring(5).trim();
        if (payload.isEmpty()) {
            return null;
        }
        if ("[DONE]".equals(payload)) {
            return GlmStreamEvent.ofEnd();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(payload);
        } catch (Exception e) {
            return null;
        }
        JsonNode choices = root.path("choices");
        JsonNode choice = choices.isArray() && !choices.isEmpty() ? choices.get(0) : null;
        String text = null;
        if (choice != null) {
            String content = choice.path("delta").path("content").asText(null);
            if (content != null && !content.isEmpty()) {
                text = content;
            }
            String finish = choice.path("finish_reason").asText(null);
            if (finish != null && !finish.isEmpty()) {
                this.finishReason = finish;
            }
        }
        JsonNode usage = root.path("usage");
        if (usage.isObject()) {
            this.promptTokens = usage.path("prompt_tokens").asLong(0);
            this.completionTokens = usage.path("completion_tokens").asLong(0);
        }
        return text != null ? GlmStreamEvent.ofText(text) : null;
    }
}
