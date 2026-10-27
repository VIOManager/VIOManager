VIOManager
黑龙江科技大学科创团队协作管理平台（泓海版）

以学期为生命周期的团队协作管理平台，7 人团队开发，覆盖 17 个业务模块：成员档案、比赛管理（比赛 → 组队 → 材料 → 成果）、项目管理、排班调度、绩效统计、技术交流、知识库与 AI 智能助手。

✨ 亮点
AI 智能助手：自研 SSE 流式编排 Agent（非框架黑盒）接入本地大模型（Ollama），多轮 Function Calling 直查真实业务数据——15 个查询工具覆盖 8 个业务域，三层权限防线（工具级角色校验 / 业务级数据范围 / 自然话术拒答），零云端成本
审批中心：RocketMQ 5 延迟消息驱动单据过期 + 本地消息表保证事件不丢；Redis 组织树 / 未读红点缓存，JMeter 实测 15 万行历史数据下红点接口 QPS 提升 361 倍、P99 由 1333ms 降至 5ms
数据范围：@DataScope 注解 + MyBatis-Plus 拦截器 + JSqlParser 动态改写 SQL，三角色数据范围对业务代码零侵入
知识库 RAG：Spring AI Alibaba + PostgreSQL / pgvector 向量检索（HNSW 索引 + 关键词混合检索）
工程质量：364 项自动化测试全绿
三端架构
端	角色	权限范围
超级管理员端	平台总管理员	全平台数据 + 学期切换 + 系统配置 + 权限调整
部门管理员端	子组织负责人	本部门（子组织）数据 + 部门内审批
普通学生端	团队成员	自己参与的赛队 / 项目 / 排班 / 材料 / 成果
核心概念
学期：一切业务数据的顶层维度
子组织：按工作性质/区域划分（数媒协会、数媒二楼等），是权限边界、排班与绩效的独立单元
赛队：比赛的参赛单位，组队/加人走部门管理员审批
项目：独立于赛队，归项目所有人个人，可被多个赛队挂载参赛
技术栈
层	技术
后端	Java 21 · Spring Boot 3.5 · Spring AI Alibaba · MyBatis-Plus
数据	MySQL · PostgreSQL + pgvector · Redis · RocketMQ 5 · MinIO
AI	Ollama 本地大模型（OpenAI 兼容协议，可切换任意兼容 LLM）
前端	Vue 3 · TypeScript · Element Plus
部署	Docker Compose
快速启动

bash
# 基础设施 + 应用（MySQL / Redis / RocketMQ / PostgreSQL+pgvector / MinIO / app）
docker compose -f backend/docker-compose.apps.yml --profile app up -d
访问 http://localhost:8088，演示账号 admin / Admin@123456
AI 助手需本地 Ollama，默认 http://localhost:11434（可用 OLLAMA_BASE_URL 覆盖），推荐 qwen2.5:7b
目录结构

text
backend/              # Spring Boot 后端（Maven 多模块）
├── vio-common/       # 通用层：JWT 认证、数据范围拦截、幂等、审计 AOP
├── vio-management/   # 主服务：17 个业务模块 + AI 助手 + 审批中心
├── vio-kb/           # 知识库服务（独立数据库）
├── vio-forum/        # 技术交流服务
└── docker/           # 容器初始化配置（含 pg-init 向量库建表）
front/                # Vue 3 前端
docs/                 # 产品需求、全局规范、接口清单、53 张表建表脚本
安全设计
JWT + BCrypt 认证；登录连败锁定（防爆破）；改密后旧 Token 即时失效
身份证号 / 证件照加密存储；上传文件强制病毒检测；下载全量审计
文档
产品需求文档
全局接口与开发规范
