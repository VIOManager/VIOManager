# VIOManager 后端

三服务：**vio-management**（管理系统，端口 8088，库 viomanager）+ **vio-kb**（知识库微服务，端口 8091，库 viomanager_kb）+ **vio-forum**（论坛独立服务，端口 8089，库 vioforum，2026-10-15 起）。技术栈与步骤全貌见 `.agentdocs/plans/2026-10-07/后端骨架-开发方案.md`，论坛拆分设计见 `.agentdocs/plans/2026-10-14/论坛-独立库与骨架-设计方案.md`，技术交流并入论坛见 `.agentdocs/plans/2026-10-15/技术交流-并入论坛-设计方案.md`。

## 工程结构

```
backend/
├── pom.xml            父工程，锁 Spring Boot 3.5.5 与依赖版本
├── docker-compose.yml 本地开发四件套（MySQL + MinIO + ClamAV + Redis；mysql-init 脚本顺带建 viomanager_kb 与 vioforum 库）
├── .env.example       环境变量样例（不建 .env 时用内置默认值，开箱即用）
├── vio-common/        底座 jar：骨架公共组件（含 JWT 过滤器与 ForumErrorCode 9100 段，业务代码禁入）
├── vio-management/    管理系统服务：启动类 + health + Flyway 建表（V1 建 52 张 + V2 移除技术交流三表 = 49 张）
├── vio-kb/            知识库微服务：KB01-17，独立库 viomanager_kb（详见模块内 README）
└── vio-forum/         论坛服务：版块/主题/楼层/点赞/收藏，独立库 vioforum（V1 十二张 + V2 退役技术推送三张 + V3 移除未用的 audit_log = 8 张业务表；论坛不进审计链路，M3 拍板）
```

## 论坛服务（vio-forum）

- **独立库 vioforum**，与 viomanager 同实例、不共享表；JWT 与管理服务同密钥共享（论坛只验签不查库，身份取自载荷）
- 启动：`mvn spring-boot:run -pl vio-forum`；⚠️ 若同 shell 导出过 `MYSQL_DATABASE`，须显式 `MYSQL_DATABASE=vioforum` 防连错库
- 验证：`curl http://localhost:8089/api/health` 返回 `code=0`
- 错误码 9100-9199（`ForumErrorCode`）：9101 锁定 / 9102 主题不存在 / 9103 楼层不存在 / 9104 版块停用（9110/9111 随技术推送域 2026-10-15 退役，码段保留不复用）；管理操作权限用通用 1003
- 前端对应 `forum/` 独立工程（端口 5175），开发期 vite 代理 `/api` → 8089；生产同域 Nginx 按最长前缀分流（`/forum/` 静态 + `/api/forum/**` → 8089）

## 环境要求

- **JDK 21**（构建时 JAVA_HOME 必须指向 21，PATH 上的 Java 11 编译不过）
- Maven 3.9+
- MySQL 8.x：用 Docker（路径 A，推荐）或本机已有实例（路径 B）

## 路径 A：Docker（推荐）

```bash
cd backend
docker compose up -d        # 起 MySQL + MinIO + ClamAV（首次拉镜像需几分钟）
mvn install                 # 构建三模块
mvn spring-boot:run -pl vio-management
```

启动日志出现 Flyway 应用 2 个迁移（V1 建表 + V2 移除技术交流三表）即完成（49 张表 + Flyway 簿记表），
验证：`curl http://localhost:8088/api/health` 返回 `code=0`。

**容器 MySQL 连接信息**（Navicat/DataGrip 新建连接用）：

| 项 | 值 |
|----|----|
| 主机 | localhost |
| 端口 | **3307**（避开本机原生 MySQL 的 3306，容器内仍是 3306） |
| 用户 / 密码 | root / dev-root-123 |
| 库 | viomanager |

常用操作：`docker compose down -v` 清库重建（Flyway 会重新建表，本地零成本）；
`docker compose up -d minio` 单独补某个容器。

## 路径 B：没有 Docker，用本机 MySQL

表结构跟应用走（Flyway），库只需要是空的：

1. 在本机 MySQL 里建空库：`CREATE DATABASE viomanager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
2. 起服务前把连接指过去（默认值是 localhost:3307，本机 MySQL 通常是 3306）：

```bash
# Git Bash
export MYSQL_PORT=3306
# PowerShell
$env:MYSQL_PORT="3306"
# CMD
set MYSQL_PORT=3306
```

3. `mvn spring-boot:run -pl vio-management`，首次启动 Flyway 自动建表（V1 + V2 后共 49 张）；跑论坛另需建空库 vioforum。

可用的环境变量全表见 `.env.example`：`MYSQL_HOST / MYSQL_PORT / MYSQL_DATABASE / MYSQL_USERNAME / MYSQL_PASSWORD / MINIO_*`。

## 注意

- **MinIO / ClamAV 容器在 Step 8（文件服务）之前不被任何代码依赖**，拉镜像失败不影响开发验证
- Step 8 之后文件上传走 MinIO + ClamAV，届时 Docker 为硬依赖，建议早装
- 改表规则：**V1、V2 禁改**（V2 已应用的 DROP 同样受 Flyway 哈希校验），只允许新增 `V3__xxx.sql` 并同步 `docs/db/initsql.sql`，经组长批准（改了直接启动报错）
