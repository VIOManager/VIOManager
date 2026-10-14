package com.viomanager.common.enums;

/**
 * 知识库内容来源（kb_doc.source_type）。
 * MANUAL=管理员上传（正文 Markdown）；LINK=外部链接收录（必填 source_url）；AI_SUMMARY=AI 总结。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum KbSourceType {

    /** 管理员上传 */
    MANUAL,

    /** 外部链接收录 */
    LINK,

    /** AI 总结 */
    AI_SUMMARY
}
