package com.viomanager.common.enums;

/**
 * 知识库内容来源（kb_doc.source_type）。
 * 2026-10-14 lzh 拍板简化后新建口径：LINK=外部链接（必填 source_url）/ FILE=上传文件（必填 attach_file_id）二选一；
 * MANUAL / AI_SUMMARY 为历史值与后续扩展（AI 总结）保留，新建接口只收 LINK / FILE。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum KbSourceType {

    /** 管理员上传（历史值：正文 Markdown 形态） */
    MANUAL,

    /** 外部链接收录 */
    LINK,

    /** AI 总结 */
    AI_SUMMARY,

    /** 上传文件（2026-10-14 拍板新增形态，两段式：先传 /api/files 拿 fileId） */
    FILE
}
