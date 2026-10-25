package com.viomanager.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * OpenAI 兼容 SSE 流解析器（云端智谱 / 本地 Ollama 同一口子）。
 * 逐行喂上游原始行（含 "data:" 前缀），返回 GlmStreamEvent；
 * finish_reason / usage / tool_calls 均为跨 chunk 聚合态，[DONE] 行触发流终止事件后读取。
 * tool_calls 按 index 聚合：id 与 function.name 首个 chunk 给全，arguments 分片到达逐段拼接。
 * 非线程安全：一条上游流一个实例。
 * 解析失败的行静默忽略（上游加字段/心跳行不炸流）——技术层兜底口径。
 */
@Getter
public class GlmSseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 聚合的结束原因：stop / length 截断 / tool_calls 要工具 */
    private String finishReason;

    /** 聚合的 usage（stream_options.include_usage 的收尾 chunk 才带，可能始终没有） */
    private Long promptTokens;
    private Long completionTokens;

    /** 按上游 index 聚合的工具调用 */
    private final Map<Integer, ToolCallAgg> toolCallAggs = new LinkedHashMap<>();

    /** 一次模型点名的工具调用（聚合完成后读取） */
    public record ToolCall(String id, String name, String arguments) {
    }

    static final class ToolCallAgg {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

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
            JsonNode delta = choice.path("delta");
            String content = delta.path("content").asText(null);
            if (content != null && !content.isEmpty()) {
                text = content;
            }
            aggregateToolCalls(delta.path("tool_calls"));
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

    /** 流终止后的工具调用清单（未聚合到任何调用返回空列表） */
    public List<ToolCall> toolCalls() {
        List<ToolCall> calls = new ArrayList<>();
        for (ToolCallAgg agg : toolCallAggs.values()) {
            calls.add(new ToolCall(agg.id, agg.name, agg.arguments.toString()));
        }
        return calls;
    }

    /** 本轮流里是否已出现过 tool_calls 增量（过程自语抑制用：出现后同轮 content 不再下发） */
    public boolean hasToolCalls() {
        return !toolCallAggs.isEmpty();
    }

    private void aggregateToolCalls(JsonNode toolCalls) {
        if (!toolCalls.isArray()) {
            return;
        }
        for (JsonNode item : toolCalls) {
            int index = item.path("index").asInt(0);
            ToolCallAgg agg = toolCallAggs.computeIfAbsent(index, k -> new ToolCallAgg());
            String id = item.path("id").asText(null);
            if (id != null && !id.isEmpty()) {
                agg.id = id;
            }
            String name = item.path("function").path("name").asText(null);
            if (name != null && !name.isEmpty()) {
                agg.name = name;
            }
            String argsPart = item.path("function").path("arguments").asText(null);
            if (argsPart != null && !argsPart.isEmpty()) {
                agg.arguments.append(argsPart);
            }
        }
    }
}
