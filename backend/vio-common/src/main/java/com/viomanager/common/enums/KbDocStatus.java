package com.viomanager.common.enums;

/**
 * 知识库文档状态（kb_doc.status，PRD §3.14 / 知识库接口清单 §5 状态机）。
 * 流转：DRAFT→PUBLISHED（发布）、PUBLISHED↔OFFLINE（上架/下架），按序流转不可跳转；
 * 实现口径：KB16 用条件 UPDATE（WHERE status=旧值）防并发跳档。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum KbDocStatus {

    /** 草稿：仅管理侧可见（KB17），浏览侧一律 9002 */
    DRAFT,

    /** 已发布：全员可见（登录即可） */
    PUBLISHED,

    /** 已下架：可重新上架回 PUBLISHED */
    OFFLINE
}
