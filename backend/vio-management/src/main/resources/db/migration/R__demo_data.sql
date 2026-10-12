-- =====================================================================
-- R__demo_data.sql —— 演示数据（Flyway repeatable：校验和变化即重跑）
--
-- ⚠️ 幂等是生死线：本文件每次改动都会整段重跑，全部用固定 ID +
--    INSERT ... AS new ON DUPLICATE KEY UPDATE（MySQL 8.0.19+ 写法，
--    老写法 VALUES() 已废弃），第二次跑走更新分支，绝不撞主键。
-- ⚠️ 生产环境禁用：上线部署前删除本文件或清空内容（见上线检查清单）。
--
-- 演示账号（BCrypt 散列与明文对照，改密码须重新生成散列）：
--   admin     / Admin@123456  超级管理员（default_password=1，演示首登强制改密提示）
--   dept_chen / Dept@123456   计算机学院部门管理员（陈志远）
--   dept_liu  / Dept@123456   电子信息学院部门管理员（刘慧）
--   stu_wang  / Stu@123456    学生 王小明（智能软件工作室，队长）
--   stu_zhao  / Stu@123456    学生 赵晓亮（嵌入式创客空间）
--
-- 固定 ID 段位（测试与后续脚本勿占）：组织 1-5、成员 101-105、账号 201-205、
-- 字典 901-904、学期 301、模板 401、比赛 402、赛队 501-502、队员 601-603、成果 701
-- =====================================================================

-- ---------------- 组织树：1 中心 → 2/3 学院 → 4/5 分子组织 ----------------
INSERT INTO sys_org (id, parent_id, name, code, org_type, is_sub_org, sort_no, path, level, status)
VALUES
    (1, 0, '大学生科创实践中心', 'CENTER',   'OTHER',     0, 1, '/0/1/',     1, 1),
    (2, 1, '计算机学院',         'CS',       'DEPARTMENT', 0, 1, '/0/1/2/',   2, 1),
    (3, 1, '电子信息学院',       'EE',       'DEPARTMENT', 0, 2, '/0/1/3/',   2, 1),
    (4, 2, '智能软件工作室',     'AI_LAB',   'SUB_ORG',    1, 1, '/0/1/2/4/', 3, 1),
    (5, 3, '嵌入式创客空间',     'EMBED_LAB','SUB_ORG',    1, 1, '/0/1/3/5/', 3, 1)
AS new
ON DUPLICATE KEY UPDATE
    parent_id = new.parent_id, name = new.name, code = new.code, org_type = new.org_type,
    is_sub_org = new.is_sub_org, sort_no = new.sort_no, path = new.path,
    level = new.level, status = new.status;

-- ---------------- 人员档案：2 教师 + 3 学生（105 故意不绑账号，演示"有档案无账号"） ----------------
INSERT INTO member (id, name, member_type, student_no, college, class_name, org_id, has_account, status)
VALUES
    (101, '陈志远', 'TEACHER', NULL,          '计算机学院', NULL,          NULL, 1, 1),
    (102, '刘慧',   'TEACHER', NULL,          '电子信息学院', NULL,        NULL, 1, 1),
    (103, '王小明', 'STUDENT', '2023010001',  '计算机学院', '软件2301班',  4,    1, 1),
    (104, '赵晓亮', 'STUDENT', '2023020002',  '电子信息学院', '电信2302班', 5,   1, 1),
    (105, '孙小雨', 'STUDENT', '2023020003',  '电子信息学院', '电信2302班', 5,   0, 1)
AS new
ON DUPLICATE KEY UPDATE
    name = new.name, member_type = new.member_type, student_no = new.student_no,
    college = new.college, class_name = new.class_name, org_id = new.org_id,
    has_account = new.has_account, status = new.status;

-- ---------------- 登录账号 5 个（密码列刻意不参与重跑更新：改过密码不被演示数据冲掉） ----------------
INSERT INTO sys_user (id, member_id, login_name, password, role, org_id, real_name, default_password, status)
VALUES
    (201, NULL, 'admin',     '$2a$10$1CyCl3pemy9eNuDvX..fieH9NB9DSPegS5oJUrBccHLu0i/c30IBa', 'SUPER_ADMIN', NULL, '系统管理员', 1, 1),
    (202, 101,  'dept_chen', '$2a$10$qBft5NRU6teDwhlbdQr3z.TLEasgrOLUmnDNXeHByG5IO/I4ZmvlO', 'DEPT_ADMIN',  2,    '陈志远',     0, 1),
    (203, 102,  'dept_liu',  '$2a$10$qBft5NRU6teDwhlbdQr3z.TLEasgrOLUmnDNXeHByG5IO/I4ZmvlO', 'DEPT_ADMIN',  3,    '刘慧',       0, 1),
    (204, 103,  'stu_wang',  '$2a$10$fKAcAgbQ.hboh5iVxtGS2O8Rh7IGIAyMAM5oK5tnzPTPVLSmi8IiW', 'STUDENT',     2,    '王小明',     0, 1),
    (205, 104,  'stu_zhao',  '$2a$10$fKAcAgbQ.hboh5iVxtGS2O8Rh7IGIAyMAM5oK5tnzPTPVLSmi8IiW', 'STUDENT',     3,    '赵晓亮',     0, 1)
AS new
ON DUPLICATE KEY UPDATE
    member_id = new.member_id, role = new.role, org_id = new.org_id,
    real_name = new.real_name, default_password = new.default_password, status = new.status;

-- ---------------- 材料类型字典（避开 SKILL：字典测试用 SKILL 造数） ----------------
INSERT INTO sys_dict_type (id, dict_code, dict_name, remark)
VALUES (901, 'MATERIAL_TYPE', '材料类型', '比赛材料分类')
AS new
ON DUPLICATE KEY UPDATE dict_name = new.dict_name, remark = new.remark;

INSERT INTO sys_dict_item (id, dict_code, item_value, item_label, source, audit_status, sort_no, status)
VALUES
    (902, 'MATERIAL_TYPE', 'photo',      '证件照',   'PRESET', 'APPROVED', 1, 1),
    (903, 'MATERIAL_TYPE', 'form',       '报名表',   'PRESET', 'APPROVED', 2, 1),
    (904, 'MATERIAL_TYPE', 'certificate','获奖证书', 'PRESET', 'APPROVED', 3, 1)
AS new
ON DUPLICATE KEY UPDATE
    item_label = new.item_label, source = new.source, audit_status = new.audit_status,
    sort_no = new.sort_no, status = new.status;

-- ---------------- 学期：当前学期 ----------------
INSERT INTO semester (id, name, status, is_current, total_weeks)
VALUES (301, '2026-2027学年第一学期', 'ACTIVE', 1, 20)
AS new
ON DUPLICATE KEY UPDATE
    name = new.name, status = new.status, is_current = new.is_current, total_weeks = new.total_weeks;

-- ---------------- 比赛模板 + 比赛（报名中） ----------------
INSERT INTO contest_template (id, name, status, sort_no)
VALUES (401, '软件类竞赛通用模板', 1, 1)
AS new
ON DUPLICATE KEY UPDATE name = new.name, status = new.status, sort_no = new.sort_no;

INSERT INTO contest (id, name, semester_id, status, source_type)
VALUES (402, '第十六届蓝桥杯程序设计大赛', 301, 'REGISTERING', 'MANUAL')
AS new
ON DUPLICATE KEY UPDATE
    name = new.name, semester_id = new.semester_id, status = new.status, source_type = new.source_type;

-- ---------------- 两支赛队：一支材料已交、一支未开始 ----------------
INSERT INTO team (id, name, contest_id, semester_id, org_id, status, material_status, material_complete_rate)
VALUES
    (501, '代码先锋队', 402, 301, 2, 'ACTIVE', 'SUBMITTED',  100.00),
    (502, '芯火电子队', 402, 301, 3, 'ACTIVE', 'NOT_STARTED',  0.00)
AS new
ON DUPLICATE KEY UPDATE
    name = new.name, contest_id = new.contest_id, semester_id = new.semester_id,
    org_id = new.org_id, status = new.status, material_status = new.material_status,
    material_complete_rate = new.material_complete_rate;

INSERT INTO team_member (id, team_id, member_id, member_name, is_leader, status)
VALUES
    (601, 501, 103, '王小明', 1, 'CONFIRMED'),
    (602, 501, 104, '赵晓亮', 0, 'UNCONFIRMED'),
    (603, 502, 105, '孙小雨', 1, 'UNCONFIRMED')
AS new
ON DUPLICATE KEY UPDATE
    member_id = new.member_id, member_name = new.member_name,
    is_leader = new.is_leader, status = new.status;

-- ---------------- 一条已生效的获奖记录（PRD §5 演示要求） ----------------
INSERT INTO achievement (id, semester_id, org_id, owner_member_id, achievement_type, name, status, submit_channel)
VALUES (701, 301, 2, 103, 'AWARD', '蓝桥杯省赛软件类一等奖', 'APPROVED', 'USER')
AS new
ON DUPLICATE KEY UPDATE
    semester_id = new.semester_id, org_id = new.org_id, owner_member_id = new.owner_member_id,
    achievement_type = new.achievement_type, name = new.name, status = new.status,
    submit_channel = new.submit_channel;
