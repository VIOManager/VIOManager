# 知识库 RAG 检索落地（拆步 3/4）：双路召回 + RRF + searchKb 第 16 工具

> 2026-10-27 · 后端支持会话 · 分支 feature/person-rework

## 做了什么

按《知识库RAG-SpringAI版-优化方案》拆步 3/4 收口检索链路——此前只有"入库"没有"用"（用户问"怎么加队伍"AI 查不到 KB）：

1. **RagSearchService（拆步 3）**：问题 → 双路召回 → RRF 融合（`score = Σ 1/(60+rank)`），topK=5，命中片段带 docId/docTitle/chunkIndex 出处。
   - 向量路 = Spring AI `similaritySearch`，阈值 0.65（实测 nomic 中文噪声地板约 0.58、原文块约 0.90）
   - 关键词路 = 自写疑问词剔除 + 中文 shingle 抽取（短串整体、长串滑窗 3-gram、单字丢弃、上限 12）+ `content ILIKE ANY` 参数化子串匹配，命中数粗排
2. **RagPgClient（关键词路新客户端）**：pg_trgm 扩展 + `gin_trgm_ops` 索引吃 ILIKE 加速。**关键落位决策**：simple 分词对中文是整句单 token 实锤——tsv @@ tsquery 形同虚设，方案 §七 预留的"pg_trgm + ILIKE"退路转正（tsv 列保留观察）。PG 连接延续 RagVectorConfig 铁律：不注册 DataSource/JdbcTemplate Bean，条件装配为普通 POGO，池上限 2，close() 随容器释放。
3. **SearchKbTool（拆步 4）**：白名单第 16 个工具（自动注册），登录即可；描述与系统提示词明确"平台使用/流程类问题走 searchKb、实时数据走 query*"分工；返回带 note——注明出自哪篇文档、检索不到时如实说明，防编造。
4. **关闭态语义**：RAG 未启用时检索入口 fail-fast 抛 9105（与同步端点同码），AiChatFlow 兜底转 `{"error":...}` 给模型转述，不炸 SSE 流；空问题返回空列表。

## 环境与部署注意

- 活库（vio-postgres 卷）已手动执行 `CREATE EXTENSION pg_trgm` + trgm GIN 索引；**pg-init/01-vector-store.sql 已同步补齐**，新装卷免手工。
- 容器部署态 `VIO_RAG_ENABLED` 默认 true（apps.yml），searchKb 上线即生效；本地 IDEA 默认 false 零装配不受影响。

## 测试与验证

- RagSearchServiceTest 4 绿（真实 PG + Ollama 往返）：喂"怎么加队伍"top5 含组队文档块且向量路参与；"B305"由关键词路兜住（双路存在理由的实证）；出处字段随行；空问题不炸。
- AiAdminToolTest：白名单恰 16 断言 + searchKb 关闭态 9105 断言，12 绿。
- AI 域全量 60 绿（含既有 RAG/聊天/限流测试零回归）；**全量回归 380 绿（mgmt 359 + kb 21）**。首跑 15 红两桩环境账非代码：vio-minio 容器漏起（文件类 12 红）+ DemoDataTest 队员恒等断言被活库漂移打红（105 被真实 API 加进 501），后者按 material_status 同款活库口径改断下限。

## 遗留（拆步 5）

- 语料 10-20 篇"平台使用指南"（从 PRD 提炼起步）——语料入库后聊天页问"怎么加队伍"才能见到带出处的真实回答；入库即走既有 KB 发布钩子/KB18 重建，无需再改代码。
- 聊天页端到端体验验证（含前端 AI 联调遗留项）。
