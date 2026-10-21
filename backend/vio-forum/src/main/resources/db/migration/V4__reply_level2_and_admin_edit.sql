-- V4：两级评论 + 管理端编辑标记（2026-10-17 组长拍板 P1/P2/P4）
-- 1) parent_reply_id：二级评论指向一级评论（两级封顶，服务层校验）；一级为 NULL
-- 2) edited：管理员编辑透明度标记（0 原文 / 1 已编辑）
-- 论坛表结构契约在本迁移文件族（initsql.sql 不含论坛表）
ALTER TABLE forum_reply
    ADD COLUMN parent_reply_id BIGINT NULL COMMENT '父评论 ID：二级指向一级；一级为 NULL（两级封顶）' AFTER quote_reply_id,
    ADD COLUMN edited TINYINT NOT NULL DEFAULT 0 COMMENT '管理员编辑标记：0 原文 1 已编辑' AFTER status,
    ADD KEY idx_forum_reply_parent (parent_reply_id, deleted, status);
