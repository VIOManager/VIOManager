# A 线基础数据接口清单：人员 / 组织 / 字典 / 学期

作者:lzh（前端） | 创建时间:2026-10-10 14:30

> 字段明细见 `front/src/api/member.ts / org.ts / dict.ts / semester.ts`（契约源）。

## 1. 人员档案（M01–M13，PRD §3.2）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/members | 分页查询（page/size/keyword/orgId/memberType/skill/hasAccount） | 管理端 |
| GET | /api/members/:id | 详情（按视角裁剪，见下） | 管理端/本人 |
| POST | /api/members | 新建（学号可空但不可重复 3001） | 管理端 |
| PUT | /api/members/:id | 编辑（学号不可改 3002） | 管理端 |
| DELETE | /api/members/:id | 删除（队长拒删 3003 / 当前登录账号拒删 3004；业务记录保留） | 管理端 |
| GET | /api/members/:id/id-card | 完整身份证号（**写审计日志**） | 超管/本部门管理员/本队队长/本人 |
| PUT | /api/members/:id/skills | 更新技能标签 | 管理端 |
| POST | /api/members/import | Excel/CSV 批量导入（multipart + 幂等键） | 管理端 |
| POST | /api/members/:id/photo | 回填证件照 fileId | 管理端 |
| POST | /api/members/:id/signature | 回填电子签名 fileId | 管理端 |
| POST | /api/members/:id/account | 绑定登录账号（初始密码 abc123） | 管理端 |
| PUT | /api/members/:id/account | 改登录名 / 重置密码（重置后回默认密码态） | 管理端 |
| GET | /api/members/import-template | 下载导入模板（文件流） | 管理端 |

**详情视角裁剪规则（M02）**：超管/部门管理员/本人 → 全量字段；其他学生 → 仅 `name/college/className/memberType/orgName/skills` 且 `limited=true`。判定在后端做，前端不传参。

**导入返回**：`{ successCount, failCount, errors: [{row, reason}] }`。

⚠️ 模板列数：PRD §3.2 文字写 13 列、字段清单 14 项，前端按 **14 列**（姓名/学号/手机号/学院/专业/班级/性别/民族/身份证号/邮箱/证件照/电子签名/所属组织/类型），以拍板为准。

## 2. 组织管理（O01–O06，PRD §3.3）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/orgs/tree | 组织树（children 嵌套，带 managerName/leaderName/sortNo/status/level） | 管理端 |
| GET | /api/orgs/colleges | 学院-专业映射（后端内置，不查库） | 登录 |
| GET | /api/orgs/:id | 节点详情 | 管理端 |
| POST | /api/orgs | 新建（parentId/name/leaderName/sortNo） | 管理端 |
| PUT | /api/orgs/:id | 编辑（改父级后端防循环引用 3006） | 管理端 |
| DELETE | /api/orgs/:id | 删除（有子组织拒删 3007；成员 org_id 置空） | 管理端 |

⚠️ leaderName 当前直接传姓名；人员选择器落地后改传 managerId（前端 TODO 联调）。

## 3. 技能字典（D01–D03，PRD §3.2）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/dicts/:type | 字典项列表（当前 type=SKILL） | 登录 |
| POST | /api/dicts/:type/items | 用户补充新项（入库即 PENDING） | 登录 |
| POST | /api/dicts/items/:itemId/audit | 管理员审核（pass=true 通过 / false 驳回） | 管理端 |

字典项结构：`{ id, label, source: PRESET|USER, auditStatus: APPROVED|PENDING|REJECTED, createdByName? }`。
**业务约束**：表单可选项 = PRESET + APPROVED；PENDING/REJECTED 不可选。页面禁止硬编码技能枚举（红线），新增业务字典一律走本组接口。

## 4. 学期与切换（S01–S08，PRD §3.4，仅超管）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | /api/semesters | 学期列表 | 登录 |
| POST | /api/semesters | 新建（名称不可重复 3008） | 超管 |
| PUT | /api/semesters/:id | 编辑 | 超管 |
| DELETE | /api/semesters/:id | 删除（有关联业务数据拒删 3009） | 超管 |
| POST | /api/semesters/switch-tasks | 发起切换（**超管密码二次确认**，密码错 2002） | 超管 |
| GET | /api/semesters/switch-tasks | 切换任务列表 | 超管 |
| POST | /api/semesters/switch-tasks/:id/cancel | 取消（30 分钟待执行窗口内） | 超管 |
| POST | /api/semesters/switch-tasks/:id/execute | 执行（**乐观锁**：body 带 version，版本不符 3011） | 超管 |

**切换任务结构**：`{ id, toSemesterName, status: PENDING|EXECUTING|SUCCESS|FAILED|CANCELLED, createdAt, deadline(30分钟窗口), impact: {contestCount, teamCount, memberRelationCount}, version }`。

**impact 影响快照**在发起时计算并固化展示（待归档比赛数/待继承赛队数/成员关系数）。执行成功的落库动作按 PRD §3.4：旧学期 ARCHIVED、已结束比赛归档、成员关系按规则继承。
