package com.viomanager.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 本地消息表实体，对应表 notify_outbox（插入点③）。
 * ⚠️ 不继承 BaseEntity：本表是系统内部事件账本，建表就没有 created_by / updated_by / deleted
 * 三类审计列（BaseEntity 注释自立规矩：审计列不全的表不要继承），主键走数据库自增。
 */
@Data
@Accessors(chain = true)
@TableName("notify_outbox")
public class NotifyOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标 topic，如 approval-status */
    private String topic;

    /** 业务类型，如 TEAM_ADD_MEMBER（事件动词在 payload 里） */
    private String bizType;

    /** 业务主键，即审批单 id */
    private Long bizId;

    /** 事件内容 JSON，含受影响 memberIds */
    private String payloadJson;

    /** PENDING 待发 / SENT 已发 / DEAD 死信 */
    private String sendStatus;

    /** 重试次数（失败退避 1/2/4/8/16 分钟，超 5 次进 DEAD） */
    private Integer retryCount;

    /** 下次重试时间；NULL = 新事件立即可发 */
    private LocalDateTime nextRetryAt;

    /** 发出时间 */
    private LocalDateTime sentAt;

    /** 最近失败原因（截断到 500 字符） */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
