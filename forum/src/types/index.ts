/**
 * 论坛站全局类型
 * 对应：《微服务拆分与论坛-架构调整方案》§5.1 论坛功能范围
 * 帖子接口类型已迁至 api/forum.ts（TS 类型即契约，主站同模式）；
 * 技术交流域 2026-10-15 并入论坛后随 api/tech.ts 一并退役
 */
/** 版块：管理员维护（当前内置四个，可扩展） */
export type BoardCode = 'GENERAL' | 'EE' | 'MODELING' | 'AI'
