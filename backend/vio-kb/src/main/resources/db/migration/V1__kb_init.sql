-- 知识库微服务基线（feature/kb）。
-- 3 张业务表列定义照搬 docs/db/initsql.sql 一字不改（表结构即契约）；
-- 另含 audit_log / idempotent_record 两张基座表（vio-common 审计与幂等切面的落库目标），
-- DDL 同主库——独立服务自带基座表是既定模式。
-- ⚠️ V1 禁改：改表只新增 V2__xxx.sql 并同步 docs/db/initsql.sql，经组长批准。

CREATE TABLE IF NOT EXISTS kb_doc (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    title               VARCHAR(300) NOT NULL            COMMENT '标题',
    category            VARCHAR(64)  NOT NULL            COMMENT '分类，对应字典 KB_CATEGORY',
    is_prompt           TINYINT      NOT NULL DEFAULT 0  COMMENT '是否 AI 提示词：1 是 0 否',
    prompt_schema       JSON         NULL                COMMENT '提示词结构模板，仅 is_prompt=1 时有值',
    content             LONGTEXT     NULL                COMMENT '正文内容（Markdown）',
    summary             VARCHAR(1000) NULL               COMMENT '摘要',
    cover_file_id       BIGINT       NULL                COMMENT '封面文件 ID',
    attach_file_id      BIGINT       NULL                COMMENT '附件文件 ID',
    source_type         VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 管理员上传 / LINK 外部链接收录 / AI_SUMMARY AI 总结',
    source_url          VARCHAR(1000) NULL               COMMENT '外部来源链接',
    tags                VARCHAR(300) NULL                COMMENT '标签，逗号分隔',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下架',
    top_flag            TINYINT      NOT NULL DEFAULT 0  COMMENT '是否置顶：1 是 0 否',
    view_count          INT          NOT NULL DEFAULT 0  COMMENT '浏览数',
    collect_count       INT          NOT NULL DEFAULT 0  COMMENT '收藏数',
    comment_count       INT          NOT NULL DEFAULT 0  COMMENT '评论数',
    ai_model            VARCHAR(100) NULL                COMMENT 'AI 总结时使用的模型名',
    org_id              BIGINT       NULL                COMMENT '归属组织',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_kb_doc_category (category, status, deleted),
    KEY idx_kb_doc_prompt (is_prompt, status, deleted),
    KEY idx_kb_doc_status (status, top_flag, deleted),
    FULLTEXT KEY ft_kb_doc (title, summary) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库文档';

CREATE TABLE IF NOT EXISTS kb_comment (
    id              BIGINT       NOT NULL                COMMENT '主键',
    doc_id          BIGINT       NOT NULL                COMMENT '文档 ID',
    parent_id       BIGINT       NULL                    COMMENT '父评论 ID，回复时为非空',
    content         VARCHAR(2000) NOT NULL               COMMENT '评论内容',
    member_id       BIGINT       NOT NULL                COMMENT '评论人人员 ID',
    member_name     VARCHAR(50)  NULL                    COMMENT '评论人姓名冗余',
    status          VARCHAR(32)  NOT NULL DEFAULT 'VISIBLE' COMMENT '状态：VISIBLE 可见 / HIDDEN 已隐藏',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_kb_comment_doc (doc_id, status, deleted, created_at),
    KEY idx_kb_comment_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库评论';

CREATE TABLE IF NOT EXISTS kb_collect (
    id              BIGINT       NOT NULL                COMMENT '主键',
    doc_id          BIGINT       NOT NULL                COMMENT '文档 ID',
    member_id       BIGINT       NOT NULL                COMMENT '收藏人人员 ID',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_kb_collect (doc_id, member_id),
    KEY idx_kb_collect_member (member_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库收藏';

-- ===== 以下两张为 vio-common 基座表（DDL 与主库一致，本服务自用） =====

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT       NOT NULL                COMMENT '主键',
    user_id         BIGINT       NULL                    COMMENT '操作人 sys_user.id，匿名时为空',
    login_name      VARCHAR(64)  NULL                    COMMENT '操作人登录名',
    real_name       VARCHAR(50)  NULL                    COMMENT '操作人姓名',
    org_id          BIGINT       NULL                    COMMENT '操作人所属组织，部门管理员按此过滤',
    role            VARCHAR(32)  NULL                    COMMENT '操作人角色',
    ip              VARCHAR(64)  NULL                    COMMENT '客户端 IP（已解析代理头）',
    user_agent      VARCHAR(300) NULL                    COMMENT 'User-Agent',
    http_method     VARCHAR(10)  NULL                    COMMENT '请求方法',
    uri             VARCHAR(500) NULL                    COMMENT '请求路径',
    biz_type        VARCHAR(64)  NOT NULL                COMMENT '业务类型，对应 @AuditLog.BizType',
    biz_type_label  VARCHAR(64)  NULL                    COMMENT '业务类型中文名',
    summary         VARCHAR(500) NULL                    COMMENT '操作摘要，SpEL 模板渲染结果',
    request_params  VARCHAR(2000) NULL                   COMMENT '请求参数摘要，敏感接口为空',
    success         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否成功：1 成功 0 失败',
    error_message   VARCHAR(1000) NULL                   COMMENT '失败原因',
    cost_millis     BIGINT       NULL                    COMMENT '耗时毫秒',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_audit_user_time (user_id, created_at),
    KEY idx_audit_org_time (org_id, created_at),
    KEY idx_audit_biz_time (biz_type, created_at),
    KEY idx_audit_time (created_at),
    KEY idx_audit_success (success, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审计日志';

CREATE TABLE IF NOT EXISTS idempotent_record (
    id              BIGINT       NOT NULL                COMMENT '主键',
    idem_key        VARCHAR(200) NOT NULL                COMMENT '幂等键（前端生成，含用户ID+业务标识+随机数）',
    biz_type        VARCHAR(64)  NOT NULL                COMMENT '业务类型',
    user_id         BIGINT       NULL                    COMMENT '发起用户',
    request_hash    VARCHAR(64)  NULL                    COMMENT '请求体哈希，用于识别同键不同参的异常',
    result_summary  VARCHAR(500) NULL                    COMMENT '首次执行结果摘要',
    expire_at       DATETIME     NOT NULL                COMMENT '过期时间，到期可清理',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem_key (idem_key),
    KEY idx_idem_expire (expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '幂等键';
