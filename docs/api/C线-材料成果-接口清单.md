# C 线材料成果接口清单：材料任务 / 提交 / 成果

作者:lzh（前端） | 创建时间:2026-10-10 14:30

> 字段明细见 `front/src/api/material.ts / achievement.ts`（契约源）。
> ⚠️ 5xxx 错误码为前端自拟暂定，后端可重排（前端只改 labels/mock 两处）。

## 1. 材料任务与要求（MT01–04 / MR01–03，PRD §3.7，管理端）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/material-tasks | 任务列表（contestId 过滤），含 requirementCount 与交齐队伍统计 |
| POST | /api/material-tasks | 新建（contestId/title/deadline）；同比赛重复建 5001 |
| PUT | /api/material-tasks/:id | 改任务名/截止时间 |
| DELETE | /api/material-tasks/:id | 删除（**任务下任一要求有提交记录拒删 5006**） |
| GET | /api/material-tasks/:id/requirements | 要求列表 |
| POST | /api/material-tasks/:id/requirements | 加要求（表单校验 1001） |
| PUT | /api/requirements/:id | 编辑要求 |
| DELETE | /api/requirements/:id | 删除（**已有提交记录拒删 5005**） |

要求结构：`{ id, taskId, type, required, formats: string[], namingRule, maxSizeMb }`。

**type 为 12 种预设枚举**（报名表/项目书/承诺书/身份证复印件/学生证复印件/团队合照/答辩PPT/研究报告/源代码/获奖证书/佐证材料/其他，值见 `front/src/config/labels.ts` MATERIAL_TYPES）。⚠️ 是否落字典表由后端定，前端当前按固定枚举处理。

## 2. 材料提交与下载（MS01–MS06）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/material-tasks/:id/overview | 管理端队伍提交矩阵（每队每要求的状态/版本） | 管理端 |
| GET | /api/my/materials | 学生端：本人每支队伍的任务/要求/提交状态 | 学生 |
| POST | /api/material-submissions | 单条提交/覆盖上传 | 在队成员 ⚠️ |
| POST | /api/material-submissions/batch | zip 批量上传（返回匹配/不匹配清单） | 在队成员 ⚠️ |
| POST | /api/material-tasks/:id/export | 打包下载队伍全部材料（body `{teamId}`） | 管理端/本队 |

**提交校验序**（POST /api/material-submissions，body `{requirementId, teamId, fileId, fileName}`）：
要求与赛队比赛匹配（5001）→ 未过截止（5002）→ 提交人是该队在册成员（5003）→ 文件扩展名在要求白名单（5004）。

**覆盖上传**：同一要求重复提交 → 旧版本记录失效（valid=false 保留痕迹）、新版本 version+1，**齐全率只按有效版本计**。

**齐全率联动（关键）**：每次提交/批量后重算该队 `completeRate = 有效提交数/要求数`，必传项未齐则队伍 `materialStatus = "待补 N 项"`、齐全则"齐全"，**该字段同时出现在 B 线赛队列表与详情**，需两张表联动更新。

**zip 批量**：前端先传文件服务拿 fileId，再调 batch；后端解压后按文件名匹配要求，返回 `{matched: [{requirementId, type}], unmatched: [文件名]}`，前端展示不匹配清单。

## 3. 成果（A01–A08，PRD §3.8）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/achievements | 列表（teamId/status/keyword 过滤）；**学生只回自己所在队伍的** | 登录 |
| GET | /api/achievements/:id | 详情（含 viewerIsLeader）；非本队成员 5007 | 管理端/本队 |
| POST | /api/achievements | 提交/录入（见下方分支） | 见下方 |
| POST | /api/achievements/:id/review | 管理员审批（body `{action: APPROVE\|REJECT, comment}`） | 管理端 |
| PUT | /api/achievements/:id/evidence | 佐证覆盖上传（body `{fileId, fileName}`） | **仅当前队长** 5007 |
| DELETE | /api/achievements/:id | 删除（仅未通过的可删；已通过不可删 1001） | 管理端 |
| POST | /api/achievements/export | 获奖导出（body `{teamId}`） | 管理端 |
| GET | /api/my/achievements | 学生端：队伍清单（含队长标识）+ 成果 | 学生 |

**提交分支**（POST /api/achievements）：学生提交必须是其队伍队长（5007）→ 状态 `PENDING` + 生成 `ACHIEVEMENT_SUBMIT` 审批单；管理员录入直接 `APPROVED` 并立即触发 §5 联动。

**成果结构**：`{ teamId, type: AWARD|RATING|QUALIFICATION|ACTIVITY|OTHER, name, level, achievedAt, description, status: PENDING|APPROVED|REJECTED, evidenceFileId, evidenceFileName }`。

**§5 联动（审批通过时，必做）**：
1. 成果状态 → APPROVED，审批单状态同步翻转；
2. 该队所在比赛的**材料任务自动新增一条"佐证材料"必交项**（若无）——type=EVIDENCE、required=true；
3. 重算该队齐全率（新增必交项未交会拉低百分比，队伍状态变"待补"）。
前端已验证：75% 齐全的队伍在成果通过后变 60% 待补 2 项。

**导出**：队伍存在未通过（PENDING/REJECTED）成果时 5008 拦截；导出内容仅含 APPROVED 的获奖（AWARD）类型。⚠️ 真实形态（文件流/异步任务）待拍板，前端当前展示 note 文案。
