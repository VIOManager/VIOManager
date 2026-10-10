# B 线赛事赛队接口清单：模板 / 比赛 / 待处理池 / 赛队

作者:lzh（前端） | 创建时间:2026-10-10 14:30

> 字段明细见 `front/src/api/contest.ts / team.ts`（契约源）。

## 1. 比赛模板（T01–T05）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/contest-templates | 模板列表（可按 category 过滤） | 管理端 |
| POST | /api/contest-templates | 新建（name/code/category/description/defaultRules） | 管理端 |
| PUT | /api/contest-templates/:id | 编辑 | 管理端 |
| DELETE | /api/contest-templates/:id | 删除（被比赛引用拒删 4012） | 管理端 |

**规则键名全组唯一（六键）**：`memberLimit / prefillLimit / allowRepeatJoin / crossTeamLeader / maxAdvisorCount / crossTrackLimit`。prefillLimit 三人队默认 **2**（lzh 2026-10-10 拍板）。

**规则三级继承**：模板 `defaultRules` → 比赛 `overrideRules` → 赛道 `overrideRules`，**JSON 只存被覆盖的键**。详情接口返回合并结果并逐键标注来源：`{ key, value, source: TEMPLATE|CONTEST|TRACK }`，合并逻辑在后端做。

## 2. 比赛与赛道（C01–C09，PRD §3.5）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/contests | 列表（semesterId/status 过滤），含 trackCount/teamCount | 登录 |
| GET | /api/contests/:id | 详情（含生效规则、赛道及各自生效规则） | 登录 |
| POST | /api/contests | 新建 | 管理端 |
| PUT | /api/contests/:id | 编辑 | 管理端 |
| DELETE | /api/contests/:id | 删除（仅 DRAFT 可删 4001） | 管理端 |
| POST | /api/contests/:id/status | 状态流转（body `{to}`），按序不可跳 4011 | 管理端 |
| POST | /api/contests/:id/tracks | 加赛道 | 管理端 |
| PUT | /api/contests/:id/tracks/:trackId | 编辑赛道 | 管理端 |
| DELETE | /api/contests/:id/tracks/:trackId | 删赛道（有赛队挂载拒删） | 管理端 |

**状态机**：`DRAFT → PUBLISHED → REGISTERING → RUNNING → FINISHED → ARCHIVED`。DRAFT→PUBLISHED 做发布前检查（基础信息/规则已配置/需赛道已配/需材料要求已配），缺项在 message 逐项指出（4001）。

## 3. 待处理比赛池（P01–P03）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/contests/pool | 池列表（status 过滤） | 管理端 |
| POST | /api/contests/pool/:id/confirm | 确认为正式比赛（按解析线索预填创建，body 即 ContestForm） | 管理端 |
| POST | /api/contests/pool/:id/ignore | 忽略 | 管理端 |

池项结构：`{ id, title, source, fetchedAt, content, clues: {signupTime?, memberLimit?, organizer?}, status: PENDING|CONFIRMED|IGNORED, confirmedContestId? }`。

## 4. 赛队（TE01–TE13，PRD §3.6）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/teams | 列表（contestId/keyword 过滤）；**学生只回自己所在的队伍** | 登录 |
| GET | /api/teams/:id | 详情；非本队成员/管理端/指导教师一律 4008 | 见左 |
| POST | /api/teams | 组队申请（**生成 TEAM_CREATE 审批单**，不直接建队） | 学生队长 |
| PUT | /api/teams/:id | 队长改队名/项目名（同比赛队名唯一 4002） | 队长/管理端 |
| GET | /api/teams/:id/members/search | 加人搜索（返回 inTeam 标记） | 队长/管理端 |
| POST | /api/teams/:id/members | 加人申请（**生成 TEAM_ADD_MEMBER 审批单**） | 队长/管理端 |
| POST | /api/teams/:id/members/:memberId/confirm | 队员本人确认/拒绝（body `{action: CONFIRM\|REJECT}`），仅本人 1003 | 队员本人 |
| DELETE | /api/teams/:id/members/:memberId | 移除成员（**生成 TEAM_REMOVE_MEMBER 审批单**；队长不可被移除 4010） | 队长/管理端 |
| POST | /api/teams/:id/exit | 非队长退出（队长不可退 4010） | 队员本人 |
| POST | /api/teams/:id/dissolve | 解散（有材料/成果/待审批拒解散 4009） | 队长/管理端 |
| POST | /api/teams/:id/advisors | 加指导教师（**生成审批单**；非教师类型 4006） | 队长/管理端 |
| DELETE | /api/teams/:id/advisors/:advisorId | 移除指导教师 | 队长/管理端 |

**组队申请校验序**（POST /api/teams）：报名期（4001）→ 队名同比赛唯一（4002）→ 重复参赛检查（4004，**lzh 2026-10-10 拍板**：受比赛规则 `allowRepeatJoin` 控制，允许则不拦；不允许时仅拦**已确认**参加同比赛其他队伍者，待确认不算）→ 预填成员数 ≤ prefillLimit（4003，三人队默认预填 **2**，同日拍板）。

**成员状态机**：`UNCONFIRMED → CONFIRMED / REJECTED`；主动退出 `EXITED`；被移除 `REMOVED`。退出/移除后立即失去材料上传、成果提交、队伍查看权限。

**详情视角（TE02/TE11，后端按 token 判定）**：返回 `viewerRole: LEADER|MEMBER|ADVISOR|ADMIN`，前端按钮显隐唯一依据。
成员行字段裁剪：管理端/队长/指导教师见全量（含 college/className/手机号脱敏/idCardLast4）；普通队员仅本人全量，他人只有姓名/角色/状态。

**列表材料字段**：`materialStatus`（"齐全"/"待补 N 项"文案）与 `materialCompleteRate`（百分比）由材料模块重算回写（见 C 线清单 §联动）。
