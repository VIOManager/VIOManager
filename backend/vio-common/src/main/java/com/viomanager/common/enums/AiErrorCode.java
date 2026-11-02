package com.viomanager.common.enums;

/**
 * 9xxx AI 助手错误码（规范 §4 段位表：9xxx 由技术交流/知识库/AI/审计四家共用——
 * 知识库占用 9001/9002，AI 助手占用 9100-9104，见《AI助手-接口清单.md》§1）。
 * 码值与含义严格照清单；其余复用 1xxx 通用码。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum AiErrorCode implements ErrorCode {

    /** AI 服务未启用（GLM key 未配置零装配，或入口开关关闭） */
    AI_NOT_ENABLED(9100, "AI 服务未启用"),

    /** 会话不存在或非本人（9101：不给试探空间，他人会话与不存在同码） */
    AI_CONVERSATION_NOT_FOUND(9101, "会话不存在或非本人"),

    /** 触发限流（每人 6 次/分；响应头带 Retry-After 秒数） */
    AI_RATE_LIMITED(9102, "问得太快了，请稍后再试"),

    /** 上游 LLM 调用失败或超时（HTTP 层直接标准 Result，不进 SSE 流） */
    AI_UPSTREAM_ERROR(9103, "AI 服务调用失败，请稍后重试"),

    /** 生成流中途异常（SSE error 事件载荷） */
    AI_STREAM_ERROR(9104, "回复生成中断，请重新生成"),

    /** 知识库向量库未启用（vio.rag.enabled=false 零装配态收到同步/检索请求，RAG 拆步 2） */
    AI_RAG_NOT_ENABLED(9105, "知识库向量库未启用");

    private final int code;
    private final String message;

    AiErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
