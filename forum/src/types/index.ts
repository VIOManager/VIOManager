/**
 * 论坛站全局类型
 * 对应：《微服务拆分与论坛-架构调整方案》§5.1 论坛功能范围
 * 帖子/推送的接口类型已迁至 api/forum.ts 与 api/tech.ts（TS 类型即契约，主站同模式）
 */
/** 版块：管理员维护（当前内置四个，可扩展） */
export type BoardCode = 'GENERAL' | 'EE' | 'MODELING' | 'AI'
