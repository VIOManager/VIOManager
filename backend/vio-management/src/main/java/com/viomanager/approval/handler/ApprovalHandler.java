package com.viomanager.approval.handler;

/**
 * 审批回调 SPI（进程内契约，不走 HTTP，规范"回调不走 HTTP"）。
 * 各业务线按业务类型实现并注册为 Spring Bean，审批中心批准 / 驳回后按 bizType 路由调用。
 * ⚠️ 实现方契约（2026-10-09 组长评审后定稿）：
 *  1. 至多一次：骨架用"条件更新 WHERE status='PENDING'"保证同一审批单的回调只执行一次，
 *    且回调在审批处理的同一事务里——事务内的数据库操作失败会连同审批结果一起回滚，
 *     实现方对纯数据库操作**无需**自建幂等
 *  2. 事务外副作用（调外部系统、发消息、写对象存储）不受回滚保护，失败重试可能重放——
 *     这类副作用须实现方按 approvalId 自行设防
 *  3. bizId 口径：更新型审批（如加队员）= 既有业务对象 ID；创建型（如建队）提交时为 null，
 *     onApproved 返回新建对象的 ID，骨架负责回填 approval.biz_id（单据可追溯）
 *  4. 骨架已提供兜底 Handler：没注册的业务类型只记日志不动作，不会静默丢失
 */
public interface ApprovalHandler {

    /** 本 Handler 负责的业务类型（与审批 bizType 词表一致），注册表按它路由 */
    String bizType();

    /**
     * 批准（含目标人同意）后回调：按快照创建/更新正式数据。
     * 返回新建的业务对象 ID（创建型审批用于回填 approval.biz_id）；更新型或无可返回时返回 null。
     */
    Long onApproved(Long approvalId, Long bizId, String payloadJson);

    /** 驳回（含目标人拒绝）后回调：各线通常无动作，留空实现即可 */
    void onRejected(Long approvalId, Long bizId, String payloadJson);
}
