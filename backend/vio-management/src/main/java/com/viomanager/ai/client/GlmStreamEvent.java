package com.viomanager.ai.client;

/**
 * GLM 流式解析产出的事件（GlmSseParser.accept 的返回值）。
 * 只区分两类：文本增量（text 非空）与流终止（end=true）；
 * finish_reason 与 usage 是跨 chunk 聚合态，流终止后从 parser 读取。
 */
public record GlmStreamEvent(String text, boolean end) {

    static GlmStreamEvent ofText(String text) {
        return new GlmStreamEvent(text, false);
    }

    static GlmStreamEvent ofEnd() {
        return new GlmStreamEvent(null, true);
    }
}
