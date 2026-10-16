# VIOManager 前端（front/）

> 视觉定调：方向一「图纸档案」（lzh 2026-10-08 拍板）——令牌见 `src/styles/tokens.css`
> 接口契约：`docs/api/`（按线拆分的接口清单；字段明细以 `src/api/*.ts` 类型定义为契约源）

## 环境

- Node 24；npm 已配 npmmirror 镜像
- 构建工具链：Vite 8 + Vue 3.5 + TypeScript **~5.9**（⚠️ 勿升 TS 7，vue-tsc 暂不兼容）+ Element Plus + Pinia + Vue Router

## 启动

```bash
npm i
cp .env.example .env.development   # 按需改 VITE_API_BASE_URL（Apifox Mock / 真实后端）
npm run dev                        # http://localhost:5174
```

> 论坛站是独立前端（`forum/`，`npm run dev` 起在 5175）。主站侧边栏底部有「VIO 论坛 ↗」入口，
> 地址由 `VITE_FORUM_URL` 配置（默认 5175）；开发期两站不同源，论坛首次进入走引导页补调试 token，
> 生产环境同网关下共享登录态。

## 容器化运行（可选，不需要装 Node）

只想看效果/给别人演示时用，开发请继续 `npm run dev`（热更新）：

```bash
docker compose up -d --build    # http://localhost:8090
```

- 容器 = nginx 托管构建产物，`/api` 同源反代到宿主机 8088 后端（`BACKEND_ORIGIN` 可改）
- 论坛入口默认指向容器版论坛站 8092（`forum/` 同样 `docker compose up -d --build`；8091 被 vio-kb 微服务占用）
- 收场：`docker compose down`（改代码后重新 `up -d --build`）

## mock 假数据与真实后端的切换（重要）

前端自带一套"假后端"（`src/api/mock/`），开发期不依赖任何服务就能跑通全部页面：

- `.env.development` 里 `VITE_USE_MOCK=true` → 走假数据（本机开发用，**该文件不进 git**）
- 不设或设 `false` → 所有请求直接打 `VITE_API_BASE_URL`（默认 `http://localhost:8088`），**clone 后默认就是走真实后端**
- 后端接口就绪后的联调动作：改 `.env.development` 的 `VITE_API_BASE_URL` 指向后端地址、`VITE_USE_MOCK=false`，然后整体删除 `src/api/mock/` 目录即可，业务代码零改动
- 字段对不上时以 `src/api/*.ts` 的 TypeScript 类型为准（与 `docs/api/` 清单一致），两边对齐改

## 演示账号（密码均为 abc123）

| 登录名 | 角色 | 对应成员 | 演示什么 |
|---|---|---|---|
| admin | 超级管理员 | 李明 | 全平台视角 + 未改默认密码的改密引导弹窗 |
| dept01 | 部门管理员 | 陈志远 | **本部门（数媒协会子树）受限视角** |
| 2023319024 | 学生 | 王雨桐 | 学生端；电波突击队队员（材料待补场景） |
| 2023319005 | 学生 | 周子墨 | 学生端；极光智创队长（队长提交成果/组队申请路径） |

演示数据全部在 `src/api/mock/*-data.ts`，**刷新页面即还原初始状态**（改动只在内存里）。

## 功能与路由

管理端（admin / dept01 菜单，dept01 无学期管理）：

| 菜单 | 路由 | 要点 |
|---|---|---|
| 01 概览统计 | /overview | 指标带为静态演示数字（接真实统计接口后自动生效） |
| 02 人员档案 | /personnel | 五条件筛选、三视角字段裁剪、Excel 导入、证件照/签名、账号绑定 |
| 03 组织管理 | /org | 组织树、3006 防循环 / 3007 拒删、技能字典审核 |
| 04 学期管理 | /semester | 仅超管；切换任务全状态机（密码二次确认 + 乐观锁） |
| 05 比赛管理 | /competition | 模板 CRUD、三级规则继承（模板→比赛→赛道，来源标注）、状态流转、待处理池 |
| 06 赛队管理 | /team | 成员状态机、视角裁剪（队长/指导教师/队员/管理端）、审批单模式 |
| 07 材料管理 | /material | 材料任务与 12 类要求配置、队伍提交矩阵、打包下载 |
| 08 成果管理 | /achievement | 队长提交走审批、§5 联动（通过后材料任务自动补佐证必交项）、获奖导出 |
| 09 审批中心 | /approval | 三视角（待办/我发起的/我是目标人）、P08 红点（顶栏+菜单+tab 角标）、详情快照与流水、批准/驳回/取消/同意/拒绝全操作、幂等键防重 |
| 10 知识库 | /kb | 三端；分类走 KB_CATEGORY 字典（系统配置维护）、置顶优先、收藏/评论、管理员发布/编辑/置顶/删除 |
| 11 项目管理 | /project | 可见性八条、成员/指导教师直加（不走审批）、赛队挂载、比赛节点审核（通过后自动同步任务）、项目文件、查看授权 |
| 11 排班管理 | /schedule | 周排班格子表（校验链 8009/8007/8006/8005/8008）、空课 7×5 矩阵（单双周计入）、课表导入与班级映射确认、时段规则配置 |
| 12 绩效统计 | /performance | 周报审核（按子组织维度打分，权重/满分快照固化）、汇总排行（自动标签）、打分规则配置 |
| 13 审计日志 | /audit | 查账列表（姓名/类型/成败/时间范围筛选）、bizType 动态枚举、详情含请求参数；部门管理员仅本部门子树 |
| 14 系统配置 | /system | 仅超管；字典类型/条目管理（停用优先于删除）、用户补充词条审核、系统参数（只读项后端拦截） |

学生端：/my/overview（我的概览：指标带聚合我的赛队/成果/材料/审批红点，学期刻度尺接真）、/my/team（组队申请）、/my/material（待补清单+zip 批量）、/my/achievement（队长提交）、/my/schedule（我的排班）、/my/performance（周报提交/驳回重提/我的汇总卡）、/kb（知识库：浏览/收藏/评论）。

> 知识库 2026-10-13 从论坛站迁回主站（PRD §3.14 原位）；论坛站只保留首页与技术交流。

## 角色数据范围演示（admin vs dept01 对照）

后端 DataScope 的前端模拟（`src/api/mock/scope.ts`）：部门管理员只看本部门组织子树。

| 看什么 | admin（全平台） | dept01（数媒协会系） |
|---|---|---|
| 概览指标 | 186 / 47 / 9 | 107 / 30 / 5 |
| 人员档案 | 8 人 | 4 人 |
| 组织树 | 根=科创团队本部 | 根=数媒协会 |
| 赛队 | 3 支 | 2 支 |
| 成果 | 3 项 | 2 项 |

越权按 ID 硬访问别部门数据一律 1003 拦截。

## 目录结构速查

```
src/
├─ api/          # 接口层：*.ts 是字段契约源；mock/ 是可整体删除的假数据层
├─ views/        # 页面（按模块分目录；学生端在 my/）
├─ components/   # 通用组件（上传 UploadField、脱敏 MaskField、改密弹窗等）
├─ config/       # menu.ts 三端菜单；labels.ts 固定枚举翻译（含 12 种材料类型）
├─ composables/  # useDict 字典钩子
└─ styles/       # tokens.css 设计令牌；page.css 业务页通用版式
```

## 验证与构建

```bash
npx vue-tsc -b    # 类型检查，须零错误
npm run build     # 类型检查 + 生产构建
```

已知事项：

- 开发模式下改完代码首次进入某页面，列表可能显示"加载中"约半秒到一秒才出数据（dev 冷编译占主线程的渲染滞后），**属正常现象，生产构建不存在**
- Element Plus 全量引入首包约 1MB，后续优化项
- 顶栏"角色切换"下拉是**开发调试件**（验证三端菜单用），联调后移除
- 内置浏览器（IAB）弹窗关闭后若页面被看不见的东西挡住，刷新即可（webview 动画帧挂起，真实 Chrome 无此问题）

## 开发约定（红线）

- 页面**零裸调 axios**：一律走 `src/api/http.ts` 的 get/post/put/del/upload
- **零硬编码枚举翻译**：固定枚举进 `config/labels.ts`，业务字典走 useDict
- 单文件 ≤300 行，超了拆 data/util 文件；代码风格朴素（for 循环 + 人话注释，禁 stream/record 等花活）
- token 注入只在 axios 实例做；用户输入禁 v-html
- 提交信息用中文 conventional 格式；对外表述用 lzh 不用"组长"
