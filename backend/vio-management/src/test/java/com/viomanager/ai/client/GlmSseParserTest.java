package com.viomanager.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * OpenAI 兼容 SSE 流解析纯单测：文本增量 / finish_reason 与 usage 跨 chunk 聚合 /
 * [DONE] 终止 / 空行注释行与坏 JSON 静默忽略。
 */
class GlmSseParserTest {

    @Test
    void emitsTextDeltas() {
        GlmSseParser parser = new GlmSseParser();
        GlmStreamEvent first = parser.accept("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}");
        assertEquals("你", first.text());
        assertFalse(first.end());
        GlmStreamEvent second = parser.accept("data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}");
        assertEquals("好", second.text());
        assertNull(parser.getFinishReason(), "中途 chunk 不应有 finish_reason");
    }

    @Test
    void aggregatesFinishAndUsageThenEnds() {
        GlmSseParser parser = new GlmSseParser();
        assertNull(parser.accept("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}"));
        assertNull(parser.getCompletionTokens(), "usage 未到时为空");
        assertNull(parser.accept("data: {\"choices\":[{\"delta\":{}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}"));
        GlmStreamEvent end = parser.accept("data: [DONE]");
        assertTrue(end.end());
        assertNull(end.text());
        assertEquals("stop", parser.getFinishReason());
        assertEquals(12L, parser.getPromptTokens());
        assertEquals(5L, parser.getCompletionTokens());
    }

    @Test
    void lengthFinishReasonKeptVerbatim() {
        GlmSseParser parser = new GlmSseParser();
        parser.accept("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}");
        assertTrue(parser.accept("data: [DONE]").end());
        assertEquals("length", parser.getFinishReason(), "映射 interrupted 是编排层的事，parser 原样上抛");
    }

    @Test
    void ignoresNoiseLines() {
        GlmSseParser parser = new GlmSseParser();
        assertNull(parser.accept(null));
        assertNull(parser.accept(""));
        assertNull(parser.accept(": keep-alive"));
        assertNull(parser.accept("event: ping"));
        assertNull(parser.accept("data: "));
        assertNull(parser.accept("data: {broken json"));
        assertNull(parser.accept("data: {\"choices\":[{\"delta\":{}}]}"), "无内容 chunk 忽略");
    }

    @Test
    void aggregatesToolCallsAcrossChunks() {
        GlmSseParser parser = new GlmSseParser();
        assertNull(parser.accept("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"id\":\"call_abc\",\"type\":\"function\","
                + "\"function\":{\"name\":\"queryMySchedule\",\"arguments\":\"\"}}]}}]}"));
        assertNull(parser.accept("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"arguments\":\"{\\\"semesterId\\\":\"}}]}}]}"));
        assertNull(parser.accept("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"arguments\":\"1}\"}}]}}]}"));
        assertNull(parser.accept("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}"));
        assertTrue(parser.accept("data: [DONE]").end());
        assertEquals("tool_calls", parser.getFinishReason());
        var calls = parser.toolCalls();
        assertEquals(1, calls.size());
        assertEquals("call_abc", calls.get(0).id());
        assertEquals("queryMySchedule", calls.get(0).name());
        assertEquals("{\"semesterId\":1}", calls.get(0).arguments(), "arguments 分片应拼接完整");
    }
}
