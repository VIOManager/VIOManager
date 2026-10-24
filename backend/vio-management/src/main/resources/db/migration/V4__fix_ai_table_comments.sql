-- ============================================================================
-- V4：修复 ai_conversation / ai_message 的乱码注释与坏默认值
-- 背景：这两张表在现库是早期手工建的乱码版（客户端字符集不对，中文全成 ?，
--       连 title 的 DEFAULT '新对话' 都坏成 '???'），V1 的 IF NOT EXISTS 静默跳过没修复；
--       两表零行数据，结构（列/索引）与 V1 完全对齐，仅注释与默认值坏。
-- 修法：逐列 MODIFY 重述完整定义（类型/NULL/DEFAULT 原样照抄 V1，仅注释修复）——
--       对乱码老库是修复，对从 V1 全新建的库是幂等无操作，两边都安全。
-- 注意：provider 列默认 'ollama' 是 V1 契约原样保留（拍板已转向 GLM，但列默认值
--       属表结构契约不动；应用层显式写 provider='glm'，不依赖列默认）。
-- ============================================================================

ALTER TABLE ai_conversation
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键',
    MODIFY COLUMN title VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题（首条提问自动截取）',
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户 sys_user.id',
    MODIFY COLUMN member_id BIGINT NULL COMMENT '所属人员 ID',
    MODIFY COLUMN org_id BIGINT NULL COMMENT '所属组织',
    MODIFY COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'ollama' COMMENT 'AI 提供方：ollama / openai',
    MODIFY COLUMN model VARCHAR(100) NULL COMMENT '模型名快照',
    MODIFY COLUMN message_count INT NOT NULL DEFAULT 0 COMMENT '消息数',
    MODIFY COLUMN last_message_at DATETIME NULL COMMENT '最后消息时间',
    MODIFY COLUMN archived TINYINT NOT NULL DEFAULT 0 COMMENT '是否归档：1 已归档 0 正常',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    COMMENT = 'AI 会话';

ALTER TABLE ai_message
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键',
    MODIFY COLUMN conversation_id BIGINT NOT NULL COMMENT '会话 ID',
    MODIFY COLUMN role VARCHAR(32) NOT NULL COMMENT '角色：user 用户 / assistant 助手 / tool 工具结果 / system 系统',
    MODIFY COLUMN content LONGTEXT NULL COMMENT '消息内容',
    MODIFY COLUMN tool_name VARCHAR(100) NULL COMMENT '调用的工具名',
    MODIFY COLUMN tool_arguments TEXT NULL COMMENT '工具入参 JSON',
    MODIFY COLUMN tool_result LONGTEXT NULL COMMENT '工具返回结果 JSON（已按权限裁剪）',
    MODIFY COLUMN tool_call_id VARCHAR(100) NULL COMMENT '工具调用 ID，关联请求与结果',
    MODIFY COLUMN incomplete TINYINT NOT NULL DEFAULT 0 COMMENT '是否不完整：1 是（中断）0 否',
    MODIFY COLUMN prompt_tokens INT NULL COMMENT '提示 token 数',
    MODIFY COLUMN completion_tokens INT NULL COMMENT '生成 token 数',
    MODIFY COLUMN cost_millis BIGINT NULL COMMENT '生成耗时毫秒',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    COMMENT = 'AI 消息';
