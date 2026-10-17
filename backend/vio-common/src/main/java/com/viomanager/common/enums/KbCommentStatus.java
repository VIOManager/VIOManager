package com.viomanager.common.enums;

/**
 * 知识库评论可见状态（kb_comment.status）。
 * VISIBLE 可见 / HIDDEN 管理员隐藏；kb_doc.comment_count 口径 = 可见数，
 * 隐藏/恢复时计数随口径 ±1（同事务）。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum KbCommentStatus {

    /** 可见 */
    VISIBLE,

    /** 已隐藏（管理员操作，可恢复） */
    HIDDEN
}
