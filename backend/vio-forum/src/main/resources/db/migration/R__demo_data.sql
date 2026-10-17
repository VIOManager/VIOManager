-- =====================================================================
-- R__demo_data.sql —— 论坛库演示数据（Flyway repeatable：校验和变化即重跑）
--
-- ⚠️ 幂等是生死线：固定 ID + INSERT ... AS new ON DUPLICATE KEY UPDATE，
--    第二次跑走更新分支，绝不撞主键。
-- ⚠️ 生产环境禁用：上线部署前删除本文件或清空内容（见上线检查清单）。
--
-- ID 段位（对照管理侧 R__demo_data 实占段，无碰撞）：
--   主题 9001-9015 / 楼层 9101-9125 / 用户快照 9601-9605 / 版块 9901-9904
--   （技术推送域 2026-10-15 退役见 V2；9011-9015 为技术向主题，承接原推送演示内容）
-- author_user_id 引用管理侧演示账号真实 ID：
--   201 系统管理员 / 202 陈志远(计算机学院) / 203 刘慧(电子信息学院)
--   204 王小明(智能软件工作室) / 205 赵晓亮(嵌入式创客空间)
-- 时间口径：老帖用固定日期；活跃帖的 last_reply_at / published_at 用 NOW()-INTERVAL
--   动态值，保证"今日热议"窗口与相对时间展示永远有数据（管理侧 expire_at 同款做法）。
-- =====================================================================

-- ---------------- 版块 4 块（9901-9904，替换前端硬编码 BOARDS） ----------------
INSERT INTO forum_board (id, code, name, description, sort_order, status, deleted)
VALUES
    (9901, 'GENERAL',  '综合', '公告、招新与跨组讨论', 1, 'NORMAL', 0),
    (9902, 'EE',       '电控', '电控组技术讨论',       2, 'NORMAL', 0),
    (9903, 'MODELING', '建模', '建模与结构设计',       3, 'NORMAL', 0),
    (9904, 'AI',       'AI',   'AI 组技术讨论',        4, 'NORMAL', 0)
AS new
ON DUPLICATE KEY UPDATE
    name = new.name, description = new.description,
    sort_order = new.sort_order, status = new.status;

-- ---------------- 用户快照 5 人（9601-9605，memberCount 数据源） ----------------
INSERT INTO forum_user (id, user_id, real_name, org_id, org_name, first_seen_at, last_active_at)
VALUES
    (9601, 201, '系统管理员', NULL, NULL,               '2026-10-08 10:00:00', NOW() - INTERVAL 2 HOUR),
    (9602, 202, '陈志远',     2,    '计算机学院',       '2026-10-08 10:05:00', NOW() - INTERVAL 3 HOUR),
    (9603, 203, '刘慧',       3,    '电子信息学院',     '2026-10-09 14:00:00', NOW() - INTERVAL 26 HOUR),
    (9604, 204, '王小明',     4,    '智能软件工作室',   '2026-10-08 11:00:00', NOW() - INTERVAL 1 HOUR),
    (9605, 205, '赵晓亮',     5,    '嵌入式创客空间',   '2026-10-09 09:30:00', NOW() - INTERVAL 20 MINUTE)
AS new
ON DUPLICATE KEY UPDATE
    real_name = new.real_name, org_id = new.org_id, org_name = new.org_name,
    last_active_at = new.last_active_at;

-- ---------------- 主题 15 帖（9001-9010 原有 + 9011-9015 技术向，2026-10-15 承接原推送域） ----------------
INSERT INTO forum_topic (id, board_id, title, body, tags, author_user_id, author_name, author_group,
                         status, pinned, reply_count, view_count, like_count, collect_count,
                         last_reply_at, last_reply_user_id, last_reply_by_name, deleted, created_at)
VALUES
    (9001, 9902, '赛季电控培训第三期：STM32 HAL 库电机闭环控制速成',
     '本周六下午两点，电控培训第三期。\n内容：HAL 库定时器编码器接口、PID 闭环、电流环入门。\n带上自己的板子，实验室提供电机与电源。',
     'STM32,电机控制', 205, '赵晓亮', '嵌入式创客空间', 'NORMAL', 1, 6, 58, 12, 3,
     NOW() - INTERVAL 3 HOUR, 204, '王小明', 0, NOW() - INTERVAL 5 HOUR),
    (9002, 9903, '建模组十月招新面试题与评分标准公示',
     '十月招新面试题已定，评分标准同步公示。\n面试分三档：基础建模、装配体设计、工程图规范。\n有异议的同学请在周三前跟帖反馈。',
     '招新', 202, '陈志远', '计算机学院', 'NORMAL', 0, 3, 41, 5, 2,
     NOW() - INTERVAL 5 HOUR, 205, '赵晓亮', 0, NOW() - INTERVAL 7 HOUR),
    (9003, 9902, 'PCB 四层板电源完整性 Layout 检查清单分享',
     '整理了一份四层板电源完整性的检查清单，踩过的坑都在里面。\n叠层选择、去耦电容摆放、过孔换层位置三块最重要。\n清单附件见实验室共享盘。',
     'PCB,电源完整性', 202, '陈志远', '计算机学院', 'NORMAL', 0, 2, 33, 8, 6,
     NOW() - INTERVAL 7 HOUR, 204, '王小明', 0, NOW() - INTERVAL 9 HOUR),
    (9004, 9902, '请教：CAN 总线偶发丢帧，终端电阻已经核对过',
     '排查两天了：120Ω 两端都量过，波特率 500K 对齐，示波器波形看着正常。\n偶发丢帧大概千分之三，优先级低的时候更明显。\n有遇到过类似情况的吗？',
     'CAN,排障', 204, '王小明', '智能软件工作室', 'NORMAL', 0, 3, 52, 4, 1,
     NOW() - INTERVAL 12 HOUR, 205, '赵晓亮', 0, NOW() - INTERVAL 30 HOUR),
    (9005, 9902, 'RoboMaster 电控复盘：步兵机器人的功率限制策略',
     '赛季结束，把步兵功率限制的方案复盘一遍。\n从最朴素的电流限幅到缓冲能量管理，踩坑过程全记录。\n明年电源设计照这个思路改。',
     'RoboMaster,功率限制', 204, '王小明', '智能软件工作室', 'NORMAL', 0, 2, 66, 15, 8,
     NOW() - INTERVAL 20 HOUR, 202, '陈志远', 0, NOW() - INTERVAL 2 DAY),
    (9006, 9904, 'AI 组：本地部署 Qwen 做赛题摘要的踩坑记录',
     '本地跑 Qwen 做历年赛题摘要，记录三个坑：\n显存不够先量化、长文本分段摘要再合并、提示词里给输出格式约束。\n效果对得起 4090 的电费。',
     'Qwen,AI,部署', 204, '王小明', '智能软件工作室', 'NORMAL', 0, 2, 45, 9, 5,
     NOW() - INTERVAL 36 HOUR, 205, '赵晓亮', 0, NOW() - INTERVAL 2 DAY),
    (9007, 9901, '工作室新到三台示波器，使用登记和注意事项',
     '新到三台示波器（型号见附件照片），已入库。\n使用前扫码登记，探头注意补偿校准，别拿它测市电。\n借用超过三天请跟值班同学报备。',
     '仪器,公告', 205, '赵晓亮', '嵌入式创客空间', 'NORMAL', 0, 2, 29, 3, 1,
     NOW() - INTERVAL 40 HOUR, 204, '王小明', 0, NOW() - INTERVAL 3 DAY),
    (9008, 9904, 'GLM 写周报的提示词模板（内测一周，反馈不错）',
     '内测一周的周报提示词模板：\n按"本周产出/数据/下周计划"三段结构生成初稿，再人工改。\n省下的时间够多调一个 bug。',
     'GLM,提示词,周报', 204, '王小明', '智能软件工作室', 'NORMAL', 0, 2, 37, 7, 4,
     NOW() - INTERVAL 50 HOUR, 205, '赵晓亮', 0, NOW() - INTERVAL 3 DAY),
    (9009, 9903, '底盘悬挂的打印件又断了，求耐打的材料方案',
     'PETG 打的悬挂臂第三场训练就断。\n试过加厚和调打印方向，效果一般。\n求耐冲击的材料或结构方案，最好有实测数据。',
     '3D打印,材料', 205, '赵晓亮', '嵌入式创客空间', 'NORMAL', 0, 2, 24, 2, 0,
     NOW() - INTERVAL 60 HOUR, 202, '陈志远', 0, NOW() - INTERVAL 4 DAY),
    (9010, 9901, '十月经费公示与耗材采买清单（已执行）',
     '十月耗材采买已执行完毕，清单与票据照片存共享盘。\n总支出与预算偏差 3% 以内，明细跟帖答疑。',
     '经费,公示', 201, '系统管理员', NULL, 'NORMAL', 0, 1, 18, 1, 0,
     NOW() - INTERVAL 70 HOUR, 202, '陈志远', 0, NOW() - INTERVAL 5 DAY),
    (9011, 9902, '分享：STM32 定时器编码器模式速览（外部教程）',
     '电子部落这篇编码器模式讲得比手册清楚，四个关键寄存器都给了配置示例。\n链接：https://example.com/stm32-timer-encoder\n配 PID 测速的同学建议先过一遍，常见坑都标出来了。',
     'STM32,定时器,教程', 204, '王小明', '智能软件工作室', 'NORMAL', 0, 0, 15, 2, 1,
     NOW() - INTERVAL 6 HOUR, 204, '王小明', 0, NOW() - INTERVAL 6 HOUR),
    (9012, 9902, 'PID 参数整定的工程口诀（组内传阅版）',
     '整定顺序没有玄学：\n先给 P 逼近目标，看震荡周期。\n再用 I 消稳态误差，积分限幅别忘。\n最后 D 抑制超调，噪声大就滤波或砍掉。\n整定过程录波形，回看比凭记忆靠谱。',
     'PID,控制', 205, '赵晓亮', '嵌入式创客空间', 'NORMAL', 0, 0, 12, 3, 1,
     NOW() - INTERVAL 20 HOUR, 205, '赵晓亮', 0, NOW() - INTERVAL 20 HOUR),
    (9013, 9903, '团队建模规范：命名与图层管理速查',
     '规范的意义是交接零成本：\n零件名"部件-功能-序号"三段式。\n图层按材质与工序分组，别把所有东西堆在默认层。\n装配体顶层只放子装配，散件一律进子装配。\n有补充直接跟帖，月底统一收进规范文档。',
     '建模,规范', 202, '陈志远', '计算机学院', 'NORMAL', 0, 0, 9, 1, 1,
     NOW() - INTERVAL 2 DAY, 202, '陈志远', 0, NOW() - INTERVAL 2 DAY),
    (9014, 9904, 'Qwen2.5 本地部署与量化入门（B站视频）',
     '从下载权重到跑通第一条推理的全流程录屏，跟着做一遍就能跑：\nhttps://example.com/qwen25-local\n注意视频里驱动版本较老，CUDA 装新版要自己对应一下。',
     'Qwen,部署,量化', 204, '王小明', '智能软件工作室', 'NORMAL', 0, 0, 21, 4, 2,
     NOW() - INTERVAL 3 DAY, 204, '王小明', 0, NOW() - INTERVAL 3 DAY),
    (9015, 9904, '提示词工程：结构化输出的三段式模板',
     '三段式模板：\n角色段告诉模型它是谁。\n约束段给出输出格式与禁区。\n示例段给一两个理想输出样例。\n套上之后 JSON 解析成功率肉眼可见地涨，周报模板就是照这个改的。',
     '提示词,LLM', 204, '王小明', '智能软件工作室', 'NORMAL', 0, 0, 17, 3, 1,
     NOW() - INTERVAL 4 DAY, 204, '王小明', 0, NOW() - INTERVAL 4 DAY)
AS new
ON DUPLICATE KEY UPDATE
    title = new.title, body = new.body, tags = new.tags,
    author_name = new.author_name, author_group = new.author_group,
    status = new.status, pinned = new.pinned,
    reply_count = new.reply_count, view_count = new.view_count,
    like_count = new.like_count, collect_count = new.collect_count,
    last_reply_at = new.last_reply_at, last_reply_by_name = new.last_reply_by_name;

-- ---------------- 楼层（9101-9125，reply_count 与主题一致；9011-9015 新主题暂无楼层） ----------------
INSERT INTO forum_reply (id, topic_id, floor_no, content, author_user_id, author_name, author_group,
                         like_count, status, deleted, created_at)
VALUES
    (9101, 9001, 1, '占楼：培训完会把讲义和 demo 代码整理上传。', 205, '赵晓亮', '嵌入式创客空间', 3, 'NORMAL', 0, NOW() - INTERVAL 5 HOUR),
    (9102, 9001, 2, '求带上电流环的示波器截图，想看波形。', 204, '王小明', '智能软件工作室', 1, 'NORMAL', 0, NOW() - INTERVAL 4 HOUR),
    (9103, 9001, 3, '回复二楼：培训现场直接演示，会拍照。', 205, '赵晓亮', '嵌入式创客空间', 0, 'NORMAL', 0, NOW() - INTERVAL 4 HOUR),
    (9104, 9001, 4, '报名，带一块 F407 的板子可以吗？', 202, '陈志远', '计算机学院', 0, 'NORMAL', 0, NOW() - INTERVAL 4 HOUR),
    (9105, 9001, 5, '可以，F1/F4 都行，例程两套都有。', 205, '赵晓亮', '嵌入式创客空间', 2, 'NORMAL', 0, NOW() - INTERVAL 3 HOUR),
    (9106, 9001, 6, '记笔记的来了，周六见。', 203, '刘慧', '电子信息学院', 0, 'NORMAL', 0, NOW() - INTERVAL 3 HOUR),
    (9107, 9002, 1, '评分标准挺细的，第二档的装配体设计给分点能再展开吗？', 204, '王小明', '智能软件工作室', 2, 'NORMAL', 0, NOW() - INTERVAL 7 HOUR),
    (9108, 9002, 2, '已更新：第二档拆成干涉检查与配合关系两个给分项。', 202, '陈志远', '计算机学院', 1, 'NORMAL', 0, NOW() - INTERVAL 6 HOUR),
    (9109, 9002, 3, '支持，透明一点好。', 205, '赵晓亮', '嵌入式创客空间', 0, 'NORMAL', 0, NOW() - INTERVAL 5 HOUR),
    (9110, 9003, 1, '去耦电容那段总结得好，收藏了。', 204, '王小明', '智能软件工作室', 1, 'NORMAL', 0, NOW() - INTERVAL 9 HOUR),
    (9111, 9003, 2, '补一条：换层过孔尽量成对贴近打。', 204, '王小明', '智能软件工作室', 2, 'NORMAL', 0, NOW() - INTERVAL 7 HOUR),
    (9112, 9004, 1, '优先级低更容易丢，查一下中断里是不是有长任务。', 205, '赵晓亮', '嵌入式创客空间', 3, 'NORMAL', 0, NOW() - INTERVAL 28 HOUR),
    (9113, 9004, 2, '查了，接收中断里有个 sprintf，挪出去试试。', 204, '王小明', '智能软件工作室', 1, 'NORMAL', 0, NOW() - INTERVAL 26 HOUR),
    (9114, 9004, 3, 'sprintf 出栈就是慢，用 snprintf + 手动拷贝更快。', 205, '赵晓亮', '嵌入式创客空间', 2, 'NORMAL', 0, NOW() - INTERVAL 12 HOUR),
    (9115, 9005, 1, '缓冲能量管理那段建议配一张状态机图。', 202, '陈志远', '计算机学院', 2, 'NORMAL', 0, NOW() - INTERVAL 2 DAY),
    (9116, 9005, 2, '已补在复盘文档里，跟帖发链接。', 204, '王小明', '智能软件工作室', 1, 'NORMAL', 0, NOW() - INTERVAL 20 HOUR),
    (9117, 9006, 1, '量化后摘要质量掉得厉害吗？', 205, '赵晓亮', '嵌入式创客空间', 0, 'NORMAL', 0, NOW() - INTERVAL 2 DAY),
    (9118, 9006, 2, 'A16 量化掉一点，分段摘要能补回来。', 204, '王小明', '智能软件工作室', 1, 'NORMAL', 0, NOW() - INTERVAL 36 HOUR),
    (9119, 9007, 1, '好东西，登记流程比以前方便多了。', 204, '王小明', '智能软件工作室', 1, 'NORMAL', 0, NOW() - INTERVAL 42 HOUR),
    (9120, 9007, 2, '登记二维码贴机器侧面了，别撕。', 204, '王小明', '智能软件工作室', 0, 'NORMAL', 0, NOW() - INTERVAL 40 HOUR),
    (9121, 9008, 1, '模板思路清晰，蹲一个完整版。', 203, '刘慧', '电子信息学院', 1, 'NORMAL', 0, NOW() - INTERVAL 54 HOUR),
    (9122, 9008, 2, '模板求分享，下周试试。', 205, '赵晓亮', '嵌入式创客空间', 1, 'NORMAL', 0, NOW() - INTERVAL 50 HOUR),
    (9123, 9009, 1, '试试 PA-CF 或尼龙碳纤，冲击件我们一直在用。', 202, '陈志远', '计算机学院', 2, 'NORMAL', 0, NOW() - INTERVAL 62 HOUR),
    (9124, 9009, 2, '再补一句：打印方向让层间受剪切，别受拉。', 202, '陈志远', '计算机学院', 1, 'NORMAL', 0, NOW() - INTERVAL 60 HOUR),
    (9125, 9010, 1, '答疑在此跟帖，看到就回。', 202, '陈志远', '计算机学院', 0, 'NORMAL', 0, NOW() - INTERVAL 70 HOUR)
AS new
ON DUPLICATE KEY UPDATE
    content = new.content, author_name = new.author_name, author_group = new.author_group,
    like_count = new.like_count, status = new.status;

-- 技术推送段（9501-9510）已随 tech_post/tech_collect 两表退役删除（V2，2026-10-15）
