package com.viomanager.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AI 会话实体（表 ai_conversation）。
 * ⚠️ 表无 created_by / updated_by 审计列，不继承 BaseEntity（同 idempotent_record 先例），
 * 只声明 id / deleted / created_at / updated_at，避免 MetaObjectHandler 填充不存在的列炸 SQL。
 */
@Data
@Accessors(chain = true)
@TableName("ai_conversation")
public class AiConversation {

    /** 雪花 ID；JSON 出参按字符串序列化（JacksonConfig） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话标题：默认"新对话"，首条提问后自动截前 20 字（服务层 AI01 维护） */
    private String title;

    /** 所属用户 sys_user.id（数据边界：仅本人可读写） */
    private Long userId;

    /** 所属人员 ID（冗余，聊天工具按人查询的关联位） */
    private Long memberId;

    /** 所属组织（冗余） */
    private Long orgId;

    /** AI 提供方：应用层固定写 glm（2026-10-24 拍板；列默认 'ollama' 是契约遗留，不依赖） */
    private String provider;

    /** 模型名快照（建会话时的配置值，未装配可为空） */
    private String model;

    /** 消息数（chat 落库时累加） */
    private Integer messageCount;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

    /** 是否归档：1 已归档 0 正常（首版无归档操作，列位保留） */
    private Integer archived;

    /** 逻辑删除标记（@TableLogic 只护 BaseMapper 与 Wrapper，手写 SQL 需显式加条件） */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
