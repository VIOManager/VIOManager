-- =====================================================================
-- V5__bootstrap_baseline.sql —— 学校服务器部署引导基线
-- 背景（2026-11-02 组长拍板"测试sql不进学校部署"）：R__demo_data.sql 演示数据已隔离到
-- db/demo 目录——本地开发默认灌（application.yml 两个 locations 都带），学校部署把
-- SPRING_FLYWAY_LOCATIONS 设为 classpath:db/migration 即不灌演示数据（见 docs/部署与使用指南.md）。
-- 没有演示数据的部署仍需三样底座，本迁移一次性给足：
--   1) 超级管理员账号（default_password=1 首登强制改密提示，初始口令见部署指南）
--   2) 业务依赖的字典类型与材料类型预设条目（SKILL 条目不灌——超管在系统配置页自建）
--   3) 系统参数占位行（CF01-02 页面数据源）
-- 幂等：本地开发库已有同 ID 行（演示数据同款），ON DUPLICATE KEY 不撞主键；
-- admin 密码列刻意不参与冲突更新——任何环境改过密码都不会被引导数据冲掉。
-- =====================================================================

-- ---------------- 超级管理员（唯一引导账号） ----------------
INSERT INTO sys_user (id, member_id, login_name, password, role, org_id, real_name, default_password, status)
VALUES
    (201, NULL, 'admin', '$2a$10$1CyCl3pemy9eNuDvX..fieH9NB9DSPegS5oJUrBccHLu0i/c30IBa', 'SUPER_ADMIN', NULL, '系统管理员', 1, 1)
ON DUPLICATE KEY UPDATE id = id;

-- ---------------- 字典类型：业务依赖三件 ----------------
INSERT INTO sys_dict_type (id, dict_code, dict_name, remark)
VALUES (901, 'MATERIAL_TYPE', '材料类型', '比赛材料分类')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), remark = VALUES(remark);

INSERT INTO sys_dict_type (id, dict_code, dict_name, remark)
VALUES
    (905, 'SKILL',       '技能标签', '成员技能下拉（PRD 3.1），用户可补充待审核'),
    (906, 'KB_CATEGORY', '知识库分类', '知识库文档分类（PRD 3.14），超管维护')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), remark = VALUES(remark);

-- ---------------- 字典条目：材料类型预设 ----------------
INSERT INTO sys_dict_item (id, dict_code, item_value, item_label, source, audit_status, sort_no, status)
VALUES
    (902, 'MATERIAL_TYPE', 'photo',      '证件照',   'PRESET', 'APPROVED', 1, 1),
    (903, 'MATERIAL_TYPE', 'form',       '报名表',   'PRESET', 'APPROVED', 2, 1),
    (904, 'MATERIAL_TYPE', 'certificate','获奖证书', 'PRESET', 'APPROVED', 3, 1)
ON DUPLICATE KEY UPDATE
    item_label = VALUES(item_label), source = VALUES(source), audit_status = VALUES(audit_status),
    sort_no = VALUES(sort_no), status = VALUES(status);

-- ---------------- 系统参数占位（CF 首批，正式键值清单待组长圈定） ----------------
-- site.notice 给的是占位文案，学校部署上线后在系统配置页改成真实公告即可（editable=1）
INSERT INTO sys_config (id, config_key, config_value, config_name, editable, remark)
VALUES
    (951, 'site.name',              '黑龙江科技大学科创协作管理平台', '平台名称',     1, '展示用占位参数，正式清单待组长圈定'),
    (952, 'site.notice',            '系统初始部署，公告请在系统配置页修改', '站点公告', 1, '展示用占位参数'),
    (953, 'dict.skill.user-submit', '1', '技能字典用户补充开关',             1, '1=允许学生补充技能词条'),
    (954, 'db.schema.version',      '52', '库表结构版本',                    0, '只读示例：editable=0 演示不可改')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value), config_name = VALUES(config_name),
    editable = VALUES(editable), remark = VALUES(remark);
