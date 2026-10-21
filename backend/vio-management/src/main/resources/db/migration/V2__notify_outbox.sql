-- 插入点③（方案 §4.③）：本地消息表——审批状态变更事件与 MQ 投递解耦。
-- 设计取舍：不用 RocketMQ 事务消息（要实现回查接口且与业务耦合），
-- 同事务落表更直观、可观测（表里能查能补发），是更通用的最终一致性答案。
-- 写入点与状态变更同一事务：approve / reject / cancel / agree / refuse（五动作共用链）+ expire；
-- relay 线程捞 PENDING 投递，失败退避 1/2/4/8/16 分钟，超 5 次进 DEAD 留痕。
-- 本表无 created_by / updated_by / deleted 审计列（系统事件账本无用户归属概念），
-- 对应实体不继承 BaseEntity；追认进 docs/db/initsql.sql（2026-10-17 组长拍板）。
CREATE TABLE notify_outbox (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    topic         VARCHAR(64)  NOT NULL COMMENT '目标 topic 如 approval-status',
    biz_type      VARCHAR(64)  NOT NULL COMMENT '业务类型 如 APPROVAL_APPROVE',
    biz_id        BIGINT       NOT NULL COMMENT '业务主键 即审批单 id',
    payload_json  VARCHAR(1000) NOT NULL COMMENT '事件内容 含受影响 memberIds',
    send_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待发 / SENT 已发 / DEAD 死信',
    retry_count   INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at DATETIME     NULL COMMENT '下次重试时间 退避递增',
    sent_at       DATETIME     NULL COMMENT '发出时间',
    error_message VARCHAR(500) NULL COMMENT '最近失败原因',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_outbox_pending (send_status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '本地消息表 状态变更与 MQ 投递解耦 同事务写入';
