-- 论坛审计表移除（审查 M3，lzh 2026-10-16 拍板选项 C：论坛不进审计链路）
-- 背景：V1 按论坛骨架方案建的 audit_log，但论坛控制器从未挂 @AuditLog（审计切面为
-- 注解驱动，@Around("@annotation(auditLog)")），此表自建库起始终空转。
-- 拍板结论：论坛行为痕迹由 forum_topic / forum_reply 自身承担（作者快照冻结、
-- status、时间戳、楼层引用），不接入审计链路；空表随本迁移删除。
-- ⚠️ 与管理库 viomanager.audit_log（§1 平台底座，管理侧审计在用）无关，勿混淆。
DROP TABLE IF EXISTS audit_log;
