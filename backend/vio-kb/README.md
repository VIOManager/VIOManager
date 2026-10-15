# vio-kb 知识库微服务

> 独立 Spring Boot 服务（feature/kb 首批交付）：17 个接口（KB01–KB17）、3 张表、9xxx 错误码段。
> 方案：`.agentdocs/plans/2026-10-14/知识库微服务-开发方案.md`；契约：`docs/api/知识库-接口清单.md`。

## 工程定位

- 独立进程（默认端口 8091）、独立库 `viomanager_kb`（与主库同 MySQL 实例、不同 schema）
- 复用 `vio-common` 底座：JWT 过滤器（无库依赖）、`@RequireRole`/`@Idempotent`/`@AuditLog`、统一响应
- 网关按 `/api/kb/**` 转发到本服务（前端零改动）；无网关时直连 8091 亦可

## 本地启动

```bash
# 1.起基础设施（与主服务共用同一套容器）
cd backend && docker compose up -d

# 2.一次性创建独立库（Flyway 只建表不建库）
docker exec -i vio-mysql mysql -uroot -pdev-root-123 \
  -e "CREATE DATABASE IF NOT EXISTS viomanager_kb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

# 3.构建并启动（JDK 21；JAVA_HOME 必须指向 21）
mvn install
mvn spring-boot:run -pl vio-kb

# 4.验证：curl http://localhost:8091/api/health 返回 code=0
```

⚠️ **JWT_SECRET 必须与主服务同值**（同一环境变量）。密钥不一致 = 主服务签发的 token 到这里全部 401。
token 由主服务登录接口签发，本服务无登录端点。

## 表结构版本

- V1：基线（3 张 kb 表 + 2 张基座表），**禁改**
- V2：2026-10-14 lzh 拍板条目简化——kb_doc 加 `file_name`/`publisher_name` 两列（冗余快照，免跨服务查询），
  已同步 `docs/db/initsql.sql`；改表只新增 V3+ 并同步该文件

## 验证口径

```bash
# 登录（主服务 8088）拿 token 后（2026-10-14 拍板契约：链接或文件二选一）：
curl -H "Authorization: Bearer <token>" -H "X-Idempotency-Key: k1" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8091/api/kb/docs \
     -d '{"title":"测试链接","sourceType":"LINK","linkUrl":"https://example.com"}'
```

## 测试

集成测试仅需 MySQL（本服务不碰 MinIO/ClamAV）：

```bash
docker exec -i vio-mysql mysql -uroot -pdev-root-123 \
  -e "CREATE DATABASE IF NOT EXISTS viomanager_kb_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mvn test -pl vio-kb
```

## 待组长确认项（动工前已提示，详见方案文档 §5）

1. `kb_comment/kb_collect.member_id` 以 JWT userId 落库（独立服务无 member 表）
2. KB17 部门管理员范围 v1 = 本组织精确匹配（子树待 org 能力跨服务化）
3. 审计写本库 `audit_log`，AQ01 跨库聚合留待集成阶段
4. 前端 `kb.ts` 暂定契约需按权威清单调整（路径 `/api/kb/items` → `/api/kb/docs`、补幂等头）
5. 公共文件改动报备：父 pom `<module>`、`common.enums` 四个新增枚举文件
