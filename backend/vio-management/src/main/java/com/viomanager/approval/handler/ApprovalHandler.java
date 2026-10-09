package com.viomanager.approval.handler;

/**
 * 审批回调 SPI（进程内契约，不走 HTTP，规范"回调不走 HTTP"）。
 * 各业务线按业务类型实现并注册为 Spring Bean，审批中心批准 / 驳回后按 bizType 路由调用。
 * ⚠️ 实现方约定：
 *  1. onApproved 必须按 biz_id 幂等——同一张单重复回调（理论不会，防御性要求）不能建两份数据
 *  2. 回调在审批处理的同一事务里执行，实现里抛异常会把审批结果一起回滚
 *  3. 骨架已提供兜底 Handler：没注册的业务类型只记日志不动作，不会静默丢失
 */
public interface ApprovalHandler {

    /** 本 Handler 负责的业务类型（与审批 bizType 词表一致），注册表按它路由 */
    String bizType();

    /** 批准（含目标人同意）后回调：按快照创建/更新正式数据 */
    void onApproved(Long bizId, String payloadJson);

    /** 驳回（含目标人拒绝）后回调：各线通常无动作，留空实现即可 */
    void onRejected(Long bizId, String payloadJson);
}
