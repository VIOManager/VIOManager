-- ============================================================================
-- V3：移除技术交流域三张表（迁往论坛独立库 vioforum）
-- 注：原名 V2__remove_forum_domain，2026-10-21 合并 main 时让号给 main 的 V2__notify_outbox；
--     内容不变（DROP IF EXISTS 幂等，已按旧名应用过的开发库重放无副作用）。
-- 背景：论坛拆分为独立服务（端口 8089）+ 独立库 vioforum（12 张表，
--       见 backend/vio-forum/.../V1__forum_init.sql）。技术交流的表结构与
--       数据责任已整体移交论坛侧，管理库不再保留这三张表。
-- 前置门禁（方案 §五，2026-10-15 执行留痕）：
--   ① 管理侧与共享文档无残留 tech 引用（grep 范围限定命令零输出）
--   ② 未合并分支无管理侧 tech 代码改动（Branch_B / c-line / backup 三分支核查）
--   ③ 论坛 T01/T03 已在 vioforum 实测通过（curl + 浏览器三角色 E2E）
-- 顺序：先删子表（collect / subscription 引用 post），再删主表。
-- 回滚：见方案 §五"a 方案"——保留本历史记录，按 V1 原 DDL 手工重建空表即可，
--       Flyway 不会重放；禁止再写引用这三张表的迁移。
-- ============================================================================
DROP TABLE IF EXISTS tech_subscription;
DROP TABLE IF EXISTS tech_collect;
DROP TABLE IF EXISTS tech_post;
