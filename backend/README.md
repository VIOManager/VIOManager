# VIOManager 后端

管理系统服务（阶段1 唯一可运行服务）。技术栈与步骤全貌见 `.agentdocs/plans/2026-10-07/后端骨架-开发方案.md`。

## 工程结构

```
backend/
├── pom.xml            父工程，锁 Spring Boot 3.5.5 与依赖版本
├── docker-compose.yml 本地开发三容器（MySQL + MinIO + ClamAV）
├── .env.example       环境变量样例（不建 .env 时用内置默认值，开箱即用）
├── vio-common/        底座 jar：骨架公共组件（Step 1 起填充，业务代码禁入）
└── vio-management/    管理系统服务：启动类 + health + Flyway 建表脚本
```

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

启动日志出现 `Successfully applied 1 migration` 即建表完成（52 张表 + Flyway 簿记表），
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

3. `mvn spring-boot:run -pl vio-management`，首次启动 Flyway 自动建 52 张表。

可用的环境变量全表见 `.env.example`：`MYSQL_HOST / MYSQL_PORT / MYSQL_DATABASE / MYSQL_USERNAME / MYSQL_PASSWORD / MINIO_*`。

## 注意

- **MinIO / ClamAV 容器在 Step 8（文件服务）之前不被任何代码依赖**，拉镜像失败不影响开发验证
- Step 8 之后文件上传走 MinIO + ClamAV，届时 Docker 为硬依赖，建议早装
- 改表规则：**V1 禁改**，只允许新增 `V2__xxx.sql` 并同步 `docs/db/initsql.sql`，经组长批准（Flyway 会校验 V1 哈希，改了直接启动报错）
