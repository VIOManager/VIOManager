-- =====================================================================
-- R__demo_data.sql —— 演示数据（Flyway repeatable：校验和变化即重跑）
--
-- ⚠️ 幂等是生死线：本文件每次改动都会整段重跑，全部用固定 ID +
--    INSERT ... ON DUPLICATE KEY UPDATE ... = VALUES(col)（MySQL 8.0/8.4 全兼容：
--    VALUES() 在 8.0.20+ 标记废弃但仍受支持），第二次跑走更新分支，绝不撞主键。
-- ⚠️ 生产环境禁用：上线部署前删除本文件或清空内容（见上线检查清单）。
--
-- 演示账号（BCrypt 散列与明文对照，改密码须重新生成散列）：
--   admin     / Admin@123456  超级管理员（default_password=1，演示首登强制改密提示）
--   dept_chen / Dept@123456   程序设计协会负责人（陈志远，部门管理员角色）
--   dept_liu  / Dept@123456   电子设计协会负责人（刘慧，部门管理员角色）
--   dept_wu   / Dept@123456   机械创新协会负责人（吴建国，部门管理员角色；2026-10-24 新增联调沙盒部门）
--   stu_wang  / Stu@123456    学生 王小明（程序设计协会队长；档案学院是电气与控制工程学院——跨学院入会演示）
--   stu_zhao  / Stu@123456    学生 赵晓亮（电子设计协会）
--   stu_sun   / Stu@123456    学生 孙小雨（电子设计协会，芯火电子队队长；2026-10-24 补账号：原种子故意
--                               无账号，致 802/803/807 三张单与队长身份全成"无人可登录"的孤儿待办）
--   stu_li    / Stu@123456    学生 李浩然（机械创新协会；无队伍无审批的"干净学生"，从建队到加指导教师
--                               的全流程联调专用）
--
-- 固定 ID 段位（测试与后续脚本勿占）：组织 1-4、成员 101-107、账号 201-208、
-- 字典 901-904、学期 301、模板 401、比赛 402、赛队 501-502、队员 601-603、
-- 成果 701-702、项目 1001、查看授权 1002、审批单 801-807、审批流水 811-820
-- =====================================================================

-- ---------------- 组织树：1 中心 → 2/3/4 三协会直挂（PRD §3.3 泓海口径） ----------------
-- 协会按工作性质划分、成员可来自任何学院（学院/专业/班级只是人员档案文本字段，不是树节点）；
-- 部门管理员 = 协会负责人（org.manager_id）。4/5 是学院制旧形状的残留节点，
-- 先删防 code 撞（带名字护栏，重跑幂等删 0 行；id 4 现在是机械创新协会，名字护栏保证不误删）。
-- 协会 4 机械创新协会是 2026-10-24 加的联调沙盒部门：全新部门 + 全新管理员吴建国 + 干净学生李浩然，
-- 待办从零开始，专供"建队 → 审批 → 邀人 → 加指导教师"全流程不碰存量数据地测。
DELETE FROM sys_org WHERE id IN (4, 5) AND name IN ('智能软件工作室', '嵌入式创客空间');
INSERT INTO sys_org (id, parent_id, name, code, org_type, is_sub_org, sort_no, path, level, status,
                     manager_id, leader_name)
VALUES
    (1, 0, '大学生科创实践中心', 'CENTER',    'OTHER',   0, 1, '/0/1/',   1, 1, NULL, NULL),
    (2, 1, '程序设计协会',       'PROG_CLUB', 'SUB_ORG', 1, 1, '/0/1/2/', 2, 1, 101,  '陈志远'),
    (3, 1, '电子设计协会',       'ELEC_CLUB', 'SUB_ORG', 1, 2, '/0/1/3/', 2, 1, 102,  '刘慧'),
    (4, 1, '机械创新协会',       'MECH_CLUB', 'SUB_ORG', 1, 3, '/0/1/4/', 2, 1, 107,  '吴建国')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), name = VALUES(name), code = VALUES(code), org_type = VALUES(org_type),
    is_sub_org = VALUES(is_sub_org), sort_no = VALUES(sort_no), path = VALUES(path),
    level = VALUES(level), status = VALUES(status), manager_id = VALUES(manager_id),
    leader_name = VALUES(leader_name);

-- ---------------- 人员档案：3 教师 + 4 学生 ----------------
-- 103 王小明档案学院是电气与控制工程学院、组织挂程序设计协会——演示"协会是大家族，成员来自任何学院"。
-- 105 孙小雨 2026-10-24 补 has_account=1：原"有档案无账号"边界演示实锤弊大于利——她是 802/803/807
--   三张单的主角兼芯火电子队队长，无账号=待处理红点没人看得见、队没人确认，联调时全程找不到人。
-- 106 李浩然 / 107 吴建国 = 2026-10-24 新增的机械创新协会班底（干净学生 + 部门管理员），全流程联调沙盒。
-- 手机号全员补齐（2026-10-23）：空值曾被前端显示成"无权查看"（数据缺失≠无权限），
-- 顺带让 M01 的 keyword 按手机号搜可演示。
INSERT INTO member (id, name, member_type, student_no, college, class_name, phone, org_id, has_account, status)
VALUES
    (101, '陈志远', 'TEACHER', NULL,         '计算机学院', NULL,           '13804510001', 2, 1, 1),
    (102, '刘慧',   'TEACHER', NULL,         '电子信息学院', NULL,         '13804510002', 3, 1, 1),
    (103, '王小明', 'STUDENT', '2023010001', '电气与控制工程学院', '自动化2301班', '13804510003', 2, 1, 1),
    (104, '赵晓亮', 'STUDENT', '2023020002', '电子信息学院', '电信2302班', '13804510004', 3, 1, 1),
    (105, '孙小雨', 'STUDENT', '2023020003', '电子信息学院', '电信2302班', '13804510005', 3, 1, 1),
    (106, '李浩然', 'STUDENT', '2023030001', '机械工程学院', '机械2301班', '13804510006', 4, 1, 1),
    (107, '吴建国', 'TEACHER', NULL,         '机械工程学院', NULL,           '13804510007', 4, 1, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), member_type = VALUES(member_type), student_no = VALUES(student_no),
    college = VALUES(college), class_name = VALUES(class_name), phone = VALUES(phone), org_id = VALUES(org_id),
    has_account = VALUES(has_account), status = VALUES(status);

-- ---------------- 登录账号 8 个（密码列刻意不参与重跑更新：改过密码不被演示数据冲掉） ----------------
-- 206-208 为 2026-10-24 新增：孙小雨补号 + 机械创新协会班底（缘由见人员档案节注释）
INSERT INTO sys_user (id, member_id, login_name, password, role, org_id, real_name, default_password, status)
VALUES
    (201, NULL, 'admin',     '$2a$10$1CyCl3pemy9eNuDvX..fieH9NB9DSPegS5oJUrBccHLu0i/c30IBa', 'SUPER_ADMIN', NULL, '系统管理员', 1, 1),
    (202, 101,  'dept_chen', '$2a$10$qBft5NRU6teDwhlbdQr3z.TLEasgrOLUmnDNXeHByG5IO/I4ZmvlO', 'DEPT_ADMIN',  2,    '陈志远',     0, 1),
    (203, 102,  'dept_liu',  '$2a$10$qBft5NRU6teDwhlbdQr3z.TLEasgrOLUmnDNXeHByG5IO/I4ZmvlO', 'DEPT_ADMIN',  3,    '刘慧',       0, 1),
    (204, 103,  'stu_wang',  '$2a$10$fKAcAgbQ.hboh5iVxtGS2O8Rh7IGIAyMAM5oK5tnzPTPVLSmi8IiW', 'STUDENT',     2,    '王小明',     0, 1),
    (205, 104,  'stu_zhao',  '$2a$10$fKAcAgbQ.hboh5iVxtGS2O8Rh7IGIAyMAM5oK5tnzPTPVLSmi8IiW', 'STUDENT',     3,    '赵晓亮',     0, 1),
    (206, 105,  'stu_sun',   '$2a$10$fKAcAgbQ.hboh5iVxtGS2O8Rh7IGIAyMAM5oK5tnzPTPVLSmi8IiW', 'STUDENT',     3,    '孙小雨',     0, 1),
    (207, 106,  'stu_li',    '$2a$10$fKAcAgbQ.hboh5iVxtGS2O8Rh7IGIAyMAM5oK5tnzPTPVLSmi8IiW', 'STUDENT',     4,    '李浩然',     0, 1),
    (208, 107,  'dept_wu',   '$2a$10$qBft5NRU6teDwhlbdQr3z.TLEasgrOLUmnDNXeHByG5IO/I4ZmvlO', 'DEPT_ADMIN',  4,    '吴建国',     0, 1)
ON DUPLICATE KEY UPDATE
    member_id = VALUES(member_id), role = VALUES(role), org_id = VALUES(org_id),
    real_name = VALUES(real_name), default_password = VALUES(default_password), status = VALUES(status);

-- ---------------- 材料类型字典（避开 SKILL：字典测试用 SKILL 造数） ----------------
INSERT INTO sys_dict_type (id, dict_code, dict_name, remark)
VALUES (901, 'MATERIAL_TYPE', '材料类型', '比赛材料分类')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), remark = VALUES(remark);

INSERT INTO sys_dict_item (id, dict_code, item_value, item_label, source, audit_status, sort_no, status)
VALUES
    (902, 'MATERIAL_TYPE', 'photo',      '证件照',   'PRESET', 'APPROVED', 1, 1),
    (903, 'MATERIAL_TYPE', 'form',       '报名表',   'PRESET', 'APPROVED', 2, 1),
    (904, 'MATERIAL_TYPE', 'certificate','获奖证书', 'PRESET', 'APPROVED', 3, 1)
ON DUPLICATE KEY UPDATE
    item_label = VALUES(item_label), source = VALUES(source), audit_status = VALUES(audit_status),
    sort_no = VALUES(sort_no), status = VALUES(status);

-- ---------------- 学期：当前学期 ----------------
INSERT INTO semester (id, name, status, is_current, total_weeks)
VALUES (301, '2026-2027学年第一学期', 'ACTIVE', 1, 20)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), status = VALUES(status), is_current = VALUES(is_current), total_weeks = VALUES(total_weeks);

-- ---------------- 比赛模板 + 比赛（报名中） ----------------
INSERT INTO contest_template (id, name, status, sort_no)
VALUES (401, '软件类竞赛通用模板', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status), sort_no = VALUES(sort_no);

INSERT INTO contest (id, name, semester_id, status, source_type)
VALUES (402, '第十六届蓝桥杯程序设计大赛', 301, 'REGISTERING', 'MANUAL')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), semester_id = VALUES(semester_id), status = VALUES(status), source_type = VALUES(source_type);

-- ---------------- 两支赛队：一支材料已交、一支未开始 ----------------
-- leader_member_id 必须带上：服务层一切队长校验读 team.leader_member_id，缺失则种子队解散/编辑/加人全部 1003（2026-10-16 冒烟抓出）
INSERT INTO team (id, name, contest_id, semester_id, org_id, leader_member_id, status, material_status, material_complete_rate)
VALUES
    (501, '代码先锋队', 402, 301, 2, 103, 'ACTIVE', 'SUBMITTED',  100.00),
    (502, '芯火电子队', 402, 301, 3, 105, 'ACTIVE', 'NOT_STARTED',  0.00)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), contest_id = VALUES(contest_id), semester_id = VALUES(semester_id),
    org_id = VALUES(org_id), leader_member_id = VALUES(leader_member_id), status = VALUES(status),
    material_status = VALUES(material_status), material_complete_rate = VALUES(material_complete_rate);

INSERT INTO team_member (id, team_id, member_id, member_name, is_leader, status)
VALUES
    (601, 501, 103, '王小明', 1, 'CONFIRMED'),
    (602, 501, 104, '赵晓亮', 0, 'UNCONFIRMED'),
    (603, 502, 105, '孙小雨', 1, 'UNCONFIRMED')
ON DUPLICATE KEY UPDATE
    member_id = VALUES(member_id), member_name = VALUES(member_name),
    is_leader = VALUES(is_leader), status = VALUES(status);

-- ---------------- 获奖记录：701 已生效；702 待审批（803 的载体，挂芯火电子队） ----------------
INSERT INTO achievement (id, semester_id, org_id, owner_member_id, owner_name, team_id, contest_id,
                         achievement_type, name, status, submit_channel)
VALUES
    (701, 301, 2, 103, '王小明', NULL, NULL, 'AWARD', '蓝桥杯省赛软件类一等奖', 'APPROVED', 'USER'),
    (702, 301, 3, 105, '孙小雨', 502, 402, 'AWARD', '全国大学生电子设计竞赛省二等奖', 'PENDING', 'USER')
ON DUPLICATE KEY UPDATE
    semester_id = VALUES(semester_id), org_id = VALUES(org_id), owner_member_id = VALUES(owner_member_id),
    owner_name = VALUES(owner_name), team_id = VALUES(team_id), contest_id = VALUES(contest_id),
    achievement_type = VALUES(achievement_type), name = VALUES(name), status = VALUES(status),
    submit_channel = VALUES(submit_channel);

-- ---------------- 项目与查看授权：807 的载体（跨协会限时查看演示） ----------------
-- 1001 挂程序设计协会（ORG 可见 = 用户组织在项目组织子树内），电子设计协会的孙小雨看不到 → 需走授权；
-- 审批责任组织 = 项目所属组织（清单 PR29），故 807 由程序设计协会 dept_chen 审批
INSERT INTO project (id, name, description, owner_member_id, owner_name, semester_id, org_id, status, visibility)
VALUES (1001, '智能小车竞速项目', '基于 STM32 的智能小车竞速，含循迹与避障模块', 103, '王小明', 301, 2, 'ACTIVE', 'ORG')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description), owner_member_id = VALUES(owner_member_id),
    owner_name = VALUES(owner_name), semester_id = VALUES(semester_id), org_id = VALUES(org_id),
    status = VALUES(status), visibility = VALUES(visibility);

INSERT INTO project_view_grant (id, project_id, applicant_id, applicant_name, applicant_org_id,
                                approval_id, grant_type, expire_at, status)
VALUES (1002, 1001, 105, '孙小雨', 3, 807, 'LIMITED', DATE_ADD(NOW(), INTERVAL 7 DAY), 'PENDING')
ON DUPLICATE KEY UPDATE
    project_id = VALUES(project_id), applicant_id = VALUES(applicant_id), applicant_name = VALUES(applicant_name),
    applicant_org_id = VALUES(applicant_org_id), approval_id = VALUES(approval_id), grant_type = VALUES(grant_type),
    expire_at = VALUES(expire_at), status = VALUES(status);

-- ---------------- 审批中心演示单：覆盖三视角与主要状态 ----------------
-- 故事线：804/805 呼应赛队 501 代码先锋队的由来；802 的目标人 105 有待处理邀请红点
--        （2026-10-24 孙小雨补账号后，stu_sun 登录 TARGET 视角才真正看得到）；
--        804/805 留 applicant_read=0 给发起人 103 造"结果未读"红点；803 是电子设计协会待办（芯火电子队成果 702）；
--        807 是程序设计协会待办（孙小雨跨协会限时查看项目 1001，授权行 1002 随批准生效）。
--        801 建队申请 2026-10-24 从王小明名下挪给李浩然：王小明已是 501 代码先锋队队长，同赛事二次建队
--        正好踩 B 线未堵的"队长重复参赛不复查"口子（联调报告 #5）；挪给无队伍的李浩然后，
--        机械创新协会 dept_wu 的待办有单可批，批准即诞生该协会第一支赛队，后续邀人/加指导教师接着演。
--        805 原为 TEAM_DISSOLVE 死类型演示单（解散实为 TE12 直删不走审批，真实流程永不产生该类型审批，
--        2026-10-23 换成移除队员被驳回，保留 REJECTED 状态与发起人未读红点的演示位）。
--        806 邀请目标 2026-10-24 从孙小雨换成李浩然：孙本就是芯火电子队队长，邀请自家队长入自家队
--        故事不通；换李浩然保留 CANCELLED 状态演示位，顺带给干净学生留一条"被邀又取消"的历史单。
-- ⚠️ payload 是回调 Handler 的机器契约（字段名对齐 TeamCommandServiceImpl / TeamCommandSupport /
--    AchievementApprovalSupport / ViewGrantServiceImpl 的真实构造），缺一个字段批准就炸
--    （801 曾缺 contestId 报 4001"比赛当前状态不允许"），改动前先对照各 Handler 的读法。
-- PENDING 单的 expire_at 用 NOW()+7 天动态值，演示库长期不过期。
INSERT INTO approval (id, biz_type, biz_id, biz_title, biz_payload, status,
                      applicant_id, applicant_name, target_member_id, target_member_name,
                      approver_org_id, approver_id, approver_name, team_id, contest_id, semester_id, org_id,
                      apply_reason, handle_comment, handled_at, expire_at,
                      applicant_read, target_read, approver_read, created_at)
VALUES
    (801, 'TEAM_CREATE', NULL, '星辰算法队·第十六届蓝桥杯程序设计大赛',
     '{"contestId":402,"contestName":"第十六届蓝桥杯程序设计大赛","trackId":null,"trackName":null,"name":"星辰算法队","projectName":"智能排课小助手","leaderMemberId":106,"leaderName":"李浩然","prefillMemberIds":[],"semesterId":301,"orgId":4}',
     'PENDING', 106, '李浩然', NULL, NULL, 4, NULL, NULL, NULL, 402, 301, 4,
     '和两名队友组队参加蓝桥杯，队名定为星辰算法队，请老师审批', NULL, NULL,
     DATE_ADD(NOW(), INTERVAL 7 DAY), 0, 0, 0, '2026-10-12 09:15:00'),

    (802, 'TEAM_ADD_MEMBER', NULL, '邀请 孙小雨 加入',
     '{"teamId":501,"memberId":105,"memberName":"孙小雨","contestId":402,"semesterId":301}',
     'PENDING', 103, '王小明', 105, '孙小雨', 2, NULL, NULL, 501, 402, 301, 2,
     '队里缺一名前端，孙小雨技能匹配，邀请入队', NULL, NULL,
     DATE_ADD(NOW(), INTERVAL 7 DAY), 0, 0, 0, '2026-10-11 16:40:00'),

    (803, 'ACHIEVEMENT_SUBMIT', NULL, '芯火电子队 成果提交：全国大学生电子设计竞赛省二等奖',
     '{"achievementId":"702","teamId":"502","name":"全国大学生电子设计竞赛省二等奖","type":"AWARD"}',
     'PENDING', 105, '孙小雨', NULL, NULL, 3, NULL, NULL, 502, 402, 301, 3,
     '芯火电子队获奖，成果证书扫描件后续补交，请审核', NULL, NULL,
     DATE_ADD(NOW(), INTERVAL 7 DAY), 0, 0, 0, '2026-10-12 10:20:00'),

    (804, 'TEAM_CREATE', 501, '创建赛队申请 · 代码先锋队（第十六届蓝桥杯）',
     '{"teamName":"代码先锋队","contestName":"第十六届蓝桥杯程序设计大赛","leaderName":"王小明"}',
     'APPROVED', 103, '王小明', NULL, NULL, 2, 202, '陈志远', 501, 402, 301, 2,
     '报名蓝桥杯，申请创建代码先锋队', '同意，注意报名截止时间', '2026-10-09 20:05:00',
     NULL, 0, 0, 1, '2026-10-09 19:30:00'),

    (805, 'TEAM_REMOVE_MEMBER', 501, '移除 赵晓亮',
     '{"teamId":501,"memberId":104,"memberName":"赵晓亮","contestId":402,"semesterId":301}',
     'REJECTED', 103, '王小明', 104, '赵晓亮', 2, 202, '陈志远', 501, 402, 301, 2,
     '赵晓亮长期未确认入队，申请移除', '先与本人沟通确认后再移除', '2026-10-10 11:45:00',
     NULL, 0, 0, 1, '2026-10-10 11:20:00'),

    (806, 'TEAM_ADD_MEMBER', NULL, '邀请李浩然加入 芯火电子队',
     '{"teamName":"芯火电子队","targetName":"李浩然"}',
     'CANCELLED', 104, '赵晓亮', 106, '李浩然', 3, NULL, NULL, 502, 402, 301, 3,
     '嵌入式方向缺人，邀请李浩然', NULL, '2026-10-10 15:10:00',
     NULL, 1, 1, 0, '2026-10-10 14:50:00'),

    (807, 'PROJECT_VIEW', 1002, '项目查看申请：智能小车竞速项目',
     '{"grantId":1002,"projectId":1001,"applicantId":105,"grantType":"LIMITED","expireAt":"2026-10-30T23:59:59"}',
     'PENDING', 105, '孙小雨', NULL, NULL, 2, NULL, NULL, NULL, NULL, 301, 3,
     '想参考该项目的传感器选型方案，申请限时查看 7 天', NULL, NULL,
     DATE_ADD(NOW(), INTERVAL 7 DAY), 0, 0, 0, '2026-10-12 08:30:00')
ON DUPLICATE KEY UPDATE
    biz_type = VALUES(biz_type), biz_id = VALUES(biz_id), biz_title = VALUES(biz_title), biz_payload = VALUES(biz_payload),
    status = VALUES(status), applicant_id = VALUES(applicant_id), applicant_name = VALUES(applicant_name),
    target_member_id = VALUES(target_member_id), target_member_name = VALUES(target_member_name),
    approver_org_id = VALUES(approver_org_id), approver_id = VALUES(approver_id), approver_name = VALUES(approver_name),
    team_id = VALUES(team_id), contest_id = VALUES(contest_id), semester_id = VALUES(semester_id), org_id = VALUES(org_id),
    apply_reason = VALUES(apply_reason), handle_comment = VALUES(handle_comment), handled_at = VALUES(handled_at),
    expire_at = VALUES(expire_at), applicant_read = VALUES(applicant_read),
    target_read = VALUES(target_read), approver_read = VALUES(approver_read), created_at = VALUES(created_at);

-- ---------------- 审批操作流水：每张单至少一条 SUBMIT，已处理的补对应动作 ----------------
INSERT INTO approval_record (id, approval_id, action, operator_id, operator_name, operator_role,
                             comment_text, from_status, to_status, created_at)
VALUES
    (811, 801, 'SUBMIT', 207, '李浩然', 'APPLICANT', '和两名队友组队参加蓝桥杯', NULL, 'PENDING', '2026-10-12 09:15:00'),
    (812, 802, 'SUBMIT', 204, '王小明', 'APPLICANT', '队里缺一名前端，邀请入队', NULL, 'PENDING', '2026-10-11 16:40:00'),
    (813, 803, 'SUBMIT', NULL, '孙小雨', 'APPLICANT', '芯火电子队获奖，提交成果', NULL, 'PENDING', '2026-10-12 10:20:00'),
    (814, 804, 'SUBMIT', 204, '王小明', 'APPLICANT', '报名蓝桥杯，申请建队', NULL, 'PENDING', '2026-10-09 19:30:00'),
    (815, 804, 'APPROVE', 202, '陈志远', 'APPROVER', '同意，注意报名截止时间', 'PENDING', 'APPROVED', '2026-10-09 20:05:00'),
    (816, 805, 'SUBMIT', 204, '王小明', 'APPLICANT', '赵晓亮长期未确认，申请移除', NULL, 'PENDING', '2026-10-10 11:20:00'),
    (817, 805, 'REJECT', 202, '陈志远', 'APPROVER', '先与本人沟通确认后再移除', 'PENDING', 'REJECTED', '2026-10-10 11:45:00'),
    (818, 806, 'SUBMIT', 205, '赵晓亮', 'APPLICANT', '嵌入式方向缺人，邀请入队', NULL, 'PENDING', '2026-10-10 14:50:00'),
    (819, 806, 'CANCEL', 205, '赵晓亮', 'APPLICANT', '改邀请其他同学，先取消', 'PENDING', 'CANCELLED', '2026-10-10 15:10:00'),
    (820, 807, 'SUBMIT', NULL, '孙小雨', 'APPLICANT', '参考传感器方案，限时查看', NULL, 'PENDING', '2026-10-12 08:30:00')
ON DUPLICATE KEY UPDATE
    approval_id = VALUES(approval_id), action = VALUES(action), operator_id = VALUES(operator_id),
    operator_name = VALUES(operator_name), operator_role = VALUES(operator_role),
    comment_text = VALUES(comment_text), from_status = VALUES(from_status),
    to_status = VALUES(to_status), created_at = VALUES(created_at);
