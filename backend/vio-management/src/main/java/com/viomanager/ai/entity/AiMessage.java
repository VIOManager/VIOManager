package com.viomanager.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AI 消息实体（表 ai_message）。
 * ⚠️ 表只有 created_at（无 updated_at / created_by / updated_by），不继承 BaseEntity。
 * role 取 user / assistant / tool / system 四值（列注释即契约）；tool_* 四列仅 tool 角色使用。
 */
@Data
@Accessors(chain = true)
@TableName("ai_message")
public class AiMessage {

    /** 雪花 ID；JSON 出参按字符串序列化（JacksonConfig） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属会话 ai_conversation.id */
    private Long conversationId;

    /** 角色：user 用户提问 / assistant 助手回复 / tool 工具结果 / system 系统 */
    private String role;

    /** 消息内容（LONGTEXT；assistant 为生成全文，tool 为可空） */
    private String content;

    /** 调用的工具名（tool 角色） */
    private String toolName;

    /** 工具入参 JSON（tool 角色） */
    private String toolArguments;

    /** 工具返回结果 JSON，已按权限裁剪（tool 角色） */
    private String toolResult;

    /** 工具调用 ID，关联请求与结果（tool 角色） */
    private String toolCallId;

    /** 是否不完整：1 中断（客户端断开 / 超限截断）0 完整——前端显示"回复可能不完整" */
    private Integer incomplete;

    /** 提示 token 数（assistant 消息 usage 统计） */
    private Integer promptTokens;

    /** 生成 token 数 */
    private Integer completionTokens;

    /** 生成耗时毫秒 */
    private Long costMillis;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
