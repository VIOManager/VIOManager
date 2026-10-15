# vio-kb 知识库微服务

> 独立 Spring Boot 服务：17 个接口（KB01–KB17）、3 张业务表 + 2 张基座表、9xxx 错误码段。
> 方案：`.agentdocs/plans/2026-10-14/知识库微服务-开发方案.md`；契约：lzh 2026-10-14 拍板简化
> （`docs/api/知识库-接口清单.md` 为简化前旧版，字段差异见 §2，更新走报备流程）。
> 2026-10-15 全链路实测通过：16/16 测试绿，发布/收藏/评论/下载/缓存/权限矩阵逐项验证。

## 1. 工程定位

- 独立进程（端口 **8091**）、独立库 `viomanager_kb`（与主库同 MySQL 实例、不同 schema）
- 复用 `vio-common` 底座：JWT 过滤器（无库验签）、`@RequireRole`/`@Idempotent`/`@AuditLog`、统一响应
- 文件不走本服务：上传/下载走主服务文件服务（8088 `/api/files`），本服务只存 fileId/fileName 快照
- 前端经代理/网关按 `/api/kb/**` 转发到本服务；推荐位带 Redis 缓存（组长拍板：缓存落本模块）

## 2. 契约要点（2026-10-14 简化拍板）

- 条目形态二选一：`sourceType = LINK`（必填 `linkUrl`）或 `FILE`（必填 `fileId + fileName`）
- 砍掉：分类、正文、AI 提示词模板、封面、标签——`KbDocCreateDTO` 不再接收
- 文件两段式：前端先 `POST /api/files` 拿 `fileId`，再建条目；下载走 `GET /api/files/{fileId}/download`
- VO 对齐前端 `KbItem`：`linkUrl / fileId / fileName / pinned / favoriteCount / publisherName` 等

## 3. 目录结构

```
com.viomanager.kb
├── controller/                  # KbDoc(主) / KbCollect / KbComment / KbHealth
├── dto/                         # Create/Update/Query/AdminQuery/Status/CommentCreate/CommentHide
├── entity/                      # KbDoc / KbComment / KbCollect（列定义与 initsql 一字不改）
├── mapper/                      # MyBatis-Plus
├── service/ + service/impl/     # 三条业务线：文档、收藏、评论
├── support/
│   ├── KbDocQuerySupport        # 列表/详情/推荐/adminList 查询与 VO 装配（LIST_COLUMNS 白名单）
│   ├── KbDocAccessSupport       # 可见性与归属判定（浏览侧仅 PUBLISHED；管理侧部门范围）
│   ├── KbRecommendCache         # 推荐位 Redis 缓存（§6.2）
│   └── TraceIdFilter            # 日志追踪
└── vo/                          # KbDocVO / KbDocDetailVO / KbCommentVO / KbCollectVO / KbPromptVO
```

## 4. 接口一览（前缀 /api/kb）

| 编号 | 接口 | 角色 | 附加约束 |
|------|------|------|----------|
| KB01 | GET /docs | 登录 | 筛选 keyword(FULLTEXT ngram)/sourceType/分页；仅 PUBLISHED；置顶优先再时间倒序 |
| KB02 | GET /docs/{id} | 登录 | 浏览数同步 +1（不刷新乐观锁版本）；返回 `favorited` / `updatedAt`（KB13 回传启用乐观锁）；非 PUBLISHED → 9002 |
| KB03 | GET /docs/recommend?limit= | 登录 | 置顶 + 最新 N 条（默认 5 上限 10）；走 Redis 缓存 |
| KB04 | GET /prompts | 登录 | AI 提示词列表（is_prompt=1 遗留数据源，新契约不再产生） |
| KB05 | POST /docs/{id}/collect | 登录 | 幂等键；重复 → 9001 |
| KB06 | DELETE /docs/{id}/collect | 登录 | 未收藏也返回成功 |
| KB07 | GET /my/collections | 登录 | 时间倒序分页，带文档摘要 |
| KB08 | GET /docs/{id}/comments | 登录 | 顶层时间正序分页，parent_id 折叠楼中楼 |
| KB09 | POST /docs/{id}/comments | 登录 | body：content/parentId(可空)；幂等键 |
| KB10 | DELETE /comments/{id} | 登录 | 本人或管理员 |
| KB11 | PUT /comments/{id}/hide | ADMIN | VISIBLE↔HIDDEN，计数随可见口径 ±1 |
| KB12 | POST /docs | ADMIN | 幂等键 + 审计；二选一校验（§2） |
| KB13 | PUT /docs/{id} | ADMIN | 审计；body 带 updatedAt 启用乐观锁 → 1007 |
| KB14 | DELETE /docs/{id} | ADMIN | 审计；逻辑删除，评论收藏不级联清 |
| KB15 | PUT /docs/{id}/top | ADMIN | 审计；原子翻转，重复点击效果稳定 |
| KB16 | PUT /docs/{id}/status | ADMIN | 审计；DRAFT/PUBLISHED/OFFLINE 条件更新防并发跳档 |
| KB17 | GET /admin/docs | ADMIN | 管理视角（含 DRAFT/OFFLINE），出参带 `status` 徽标；部门管理员限本部门 |
| — | GET /api/health | 匿名 | 健康检查 |

写接口（KB05/09/12）需带 `X-Idempotency-Key` 请求头（全站规范 §6，缺失 → 1001）。

## 5. 表结构版本（Flyway）

- **V1**：基线（3 张 kb 表 + audit_log/idempotent_record 基座表），**禁改**
- **V2**：2026-10-14 拍板简化——kb_doc 加 `file_name`（附件原始名，列表展示与下载兜底）、
  `publisher_name`（发布人姓名，JWT realName 快照）两列，已同步 `docs/db/initsql.sql`；
  改表只新增 V3+ 并同步该文件
- 遗留列（category/is_prompt/prompt_schema/content/cover_file_id/tags 等）按"V1 禁改"保留，
  现契约不写入：category 固定默认"未分类"，source_type 只写 LINK/FILE；QueryDTO 的 category
  参数兼容旧前端，新前端不传

## 6. 关键机制

### 6.1 两段式文件上传

```
前端选文件 → POST 8088/api/files (bizType=KB_FILE, 幂等键) → {fileId, scanStatus, originalName}
          → POST 8091/api/kb/docs {sourceType:FILE, fileId, fileName}
下载      → GET 8088/api/files/{fileId}/download（文件服务鉴权流式回写）
```

文件上限与扩展名白名单由主服务 `application.yml` 统一管（200MB；视频 mp4/mov/avi/mkv/webm 已放行）；
下载权限：`KB_FILE` 属公共资源，登录即可下（文件服务矩阵 2026-10-15 补充规则），条目可见性（PUBLISHED）由本服务把关。

### 6.2 推荐位缓存（KbRecommendCache）

- 键 `vio:kb:recommend:{limit}` 按上限分键，TTL 60s
- 写操作（发布/编辑/删除/置顶/状态流转）调 `invalidate()` 清 limit=1..10 全部键
- Redis 故障：`timeout: 1s` 快速失败 + 30s 熔断，期内直查库降级，不影响接口可用性
- 业务查询刻意放在 Redis try 之外——业务异常不背缓存故障的锅

### 6.3 乐观锁 / 幂等 / 审计

- 乐观锁：更新带 `updatedAt`（详情/列表出参已含，前端透传即可启用）比对，冲突 → 1007；
  浏览/收藏/评论计数更新**刻意不刷新** `updated_at`（计数非内容修改，否则"打开详情→保存"必 1007）
- 幂等：写接口 `@Idempotent`，写本库 idempotent_record
- 审计：管理写操作 `@AuditLog(bizType="KB")`，写本库 audit_log

### 6.4 权限矩阵（2026-10-15 实测）

| 操作 | 学生 | 部门管理员 | 超管 |
|------|------|-----------|------|
| 浏览/搜索/详情/收藏/评论 | ✅ | ✅ | ✅ |
| 发布/编辑/删除/置顶/上下架/管理列表 | ❌ 1003 | ✅（限本部门） | ✅ |

未登录 → 1002（JWT 过滤器拦截）。view_count 详情同步 +1；collect/comment 计数同事务 ±1（口径 = 可见数）。

## 7. 本地启动与配置

`application.yml` 默认值即本机开发值（MySQL 3306/root、Redis localhost:6379），环境变量可覆盖：
`MYSQL_PORT / MYSQL_PASSWORD / REDIS_HOST / REDIS_PORT / REDIS_TIMEOUT / REDIS_CONNECT_TIMEOUT`。

```bash
# 依赖：本机 MySQL(3306)、Redis(6379) 已起；Flyway 只建表不建库，首次需建库：
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS viomanager_kb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
# 构建（JDK 21）并启动：
mvn install && java -jar vio-kb/target/vio-kb-0.1.0-SNAPSHOT.jar
# 验证：curl http://localhost:8091/api/health 返回 code=0
```

⚠️ **JWT_SECRET 必须与主服务同值**（同一环境变量）。密钥不一致 = 主服务签发的 token 到这里全部 401。
token 由主服务登录接口签发，本服务无登录端点。前端联调走 Vite 代理 `/api/kb → 8091`（其余 `/api → 8088`）。

## 8. 测试（KbSafetyTest，16 例）

覆盖：匿名/越权/资源不存在/二选一校验/FILE 字段回显/乐观锁/收藏防重/隐藏评论计数等安全口径；
夹具用直接 SQL 插数，不依赖演示数据，仅依赖 MySQL。

```bash
export JAVA_HOME=<JDK21>                          # 系统 Java 8 会报 class file version 65
export MYSQL_PORT=3306 MYSQL_PASSWORD=root        # yml 默认已对，防环境差异
mvn -pl vio-kb test
```

## 9. 待组长确认项（承方案文档 §5）

1. `kb_comment/kb_collect.member_id` 以 JWT userId 落库（独立服务无 member 表）
2. KB17 部门管理员范围 v1 = 本组织精确匹配（子树待 org 能力跨服务化）
3. 审计写本库 `audit_log`，AQ01 跨库聚合留待集成阶段
4. ~~前端 kb.ts 暂定契约需调整~~ → 已本地对齐（docs 路径/幂等头/收藏语义），待 lzh 按正式清单复核提交
5. PR 报备项：父 pom `<module>`、`common.enums` 新增枚举、`initsql.sql` 同步（公共文件）
