package com.viomanager.common.enums;

/**
 * 9xxx 知识库错误码（规范 §4 段位表：9xxx 由技术交流/知识库/AI/审计四家共用，
 * 码位占用以 Apifox 各域错误码表登记为准——知识库占用 9001/9002，AI 助手后续从 9xxx 续段）。
 * 码值与含义严格照《知识库-接口清单.md》§1 错误码初始表；其余复用 1xxx 通用码。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum KbErrorCode implements ErrorCode {

    /** 重复收藏该文档（uk_kb_collect 唯一键拦截，DuplicateKeyException 转本码而非 500） */
    KB_ALREADY_COLLECTED(9001, "重复收藏该文档"),

    /** 文档不存在或已下架（浏览侧对非 PUBLISHED 一律本码，不给试探空间） */
    KB_DOC_NOT_AVAILABLE(9002, "文档不存在或已下架");

    private final int code;
    private final String message;

    KbErrorCode(int code, String message) {
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
