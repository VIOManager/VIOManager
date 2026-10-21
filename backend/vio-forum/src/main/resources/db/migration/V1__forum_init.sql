-- ============================================================================
-- V1__forum_init.sql —— 论坛库（vioforum）初始建表草案 v0.4
-- 用途：《论坛-独立库与骨架-设计方案》§三 的完整 DDL 落点（文档只引用本文件，不再内联）。
--       实施时本文件整段拷入 backend/vio-forum/src/main/resources/db/migration/V1__forum_init.sql。
-- 规矩（同管理库 V1）：V1 禁改，改表只新增 V2+ 并同步 docs/db/initsql-forum.sql；
--       库名由数据源连接串决定（本地默认 vioforum），本脚本不建库。
-- 共 12 张 = 基础设施 2 + 技术交流域 3（自管理库 V1 迁入，collect/subscription 的
--       member_id 统一改名 user_id，语义 sys_user.id——§八决策 2a）+ 团队论坛域 6 + 用户快照 1。
-- 演示数据 ID 段位见方案 §五对照表（论坛自有行 9001+，author_user_id 引用管理侧 201-205）。
-- ============================================================================

-- ############################################################################
-- 一、基础设施（2 张）：骨架组件（幂等/审计）依赖，与管理库 V1 同构复制
-- ############################################################################

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

-- ############################################################################
-- 二、技术交流域（3 张）：自管理库 V1 迁入。tech_post 结构不变；
--     tech_collect / tech_subscription 的 member_id 改名 user_id（语义 sys_user.id，§八 2a）
--     ⚠️ T10 管理员手工录入必须显式写 status='PUBLISHED' 与 published_at=NOW()
--        （列默认 PENDING 是给阶段2 RSS/AI 来源的，吃默认值前端一条都看不到）
-- ############################################################################

CREATE TABLE IF NOT EXISTS tech_post (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    title               VARCHAR(300) NOT NULL            COMMENT '标题',
    category            VARCHAR(64)  NOT NULL            COMMENT '分类，本期论坛侧静态枚举白名单（EE/MODELING/AI/VIDEO/DEV/OTHER，对齐前端 FEED_CATEGORIES；字典化阶段2）',
    content_type        VARCHAR(32)  NOT NULL DEFAULT 'ARTICLE' COMMENT '内容形态：VIDEO 视频 / ARTICLE 文章 / TUTORIAL 教程链接',
    summary             VARCHAR(1000) NULL               COMMENT '摘要',
    content             TEXT         NULL                COMMENT '正文内容（文章类）',
    cover_file_id       BIGINT       NULL                COMMENT '封面图文件 ID（预留，本期不用）',
    source_url          VARCHAR(1000) NULL               COMMENT '原始链接（视频/教程/外部文章）',
    source_name         VARCHAR(200) NULL                COMMENT '来源名称，如「B站」「公众号XXX」',
    source_type         VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 管理员录入 / RSS 外部订阅 / AI_GENERATED AI 生成 / CRAWLER 抓取',
    dedup_key           VARCHAR(300) NULL                COMMENT '去重键：source_url 的哈希（手工录入为空）',
    tags                VARCHAR(300) NULL                COMMENT '标签，逗号分隔，用于订阅推送与筛选',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态机：PENDING 待审核（阶段2 RSS/AI）/ PUBLISHED 已发布 / WITHDRAWN 已撤回；MANUAL 录入显式写 PUBLISHED',
    top_flag            TINYINT      NOT NULL DEFAULT 0  COMMENT '置顶：1 是 0 否',
    view_count          INT          NOT NULL DEFAULT 0  COMMENT '浏览数',
    collect_count       INT          NOT NULL DEFAULT 0  COMMENT '收藏数',
    published_at        DATETIME     NULL                COMMENT '发布时间',
    ai_model            VARCHAR(100) NULL                COMMENT 'AI 生成时使用的模型名',
    org_id              BIGINT       NULL                COMMENT '归属组织',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_post_dedup (dedup_key, deleted),
    KEY idx_tech_post_status (status, top_flag, published_at, deleted),
    KEY idx_tech_post_category (category, status, deleted),
    KEY idx_tech_post_type (content_type, status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '技术交流内容';

CREATE TABLE IF NOT EXISTS tech_collect (
    id              BIGINT       NOT NULL                COMMENT '主键',
    post_id         BIGINT       NOT NULL                COMMENT '内容 ID',
    user_id         BIGINT       NOT NULL                COMMENT '收藏人 sys_user.id（JWT userId，原管理库列名 member_id，§八 2a 迁入时统一）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_collect (post_id, user_id),
    KEY idx_tech_collect_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '技术交流收藏';

CREATE TABLE IF NOT EXISTS tech_subscription (
    id              BIGINT       NOT NULL                COMMENT '主键',
    user_id         BIGINT       NOT NULL                COMMENT '订阅人 sys_user.id（原 member_id，§八 2a）',
    category        VARCHAR(64)  NOT NULL                COMMENT '订阅分类',
    tag             VARCHAR(64)  NULL                    COMMENT '订阅标签，为空表示订阅整个分类',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 0 关闭',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_subscription (user_id, category, tag),
    KEY idx_tech_subscription_user (user_id, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '技术交流订阅';

-- ############################################################################
-- 三、团队论坛域（6 张）
--     点赞/收藏三表走硬删（无 deleted 列，uk 与硬删配合才成立，不许改软删）
-- ############################################################################

CREATE TABLE IF NOT EXISTS forum_board (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    code            VARCHAR(32)  NOT NULL                COMMENT '版块编码：GENERAL/EE/MODELING/AI（前端路由与筛选用）',
    name            VARCHAR(64)  NOT NULL                COMMENT '版块名',
    description     VARCHAR(200) NULL                    COMMENT '版块简介',
    sort_order      INT          NOT NULL DEFAULT 0      COMMENT '排序号，小的在前',
    status          VARCHAR(32)  NOT NULL DEFAULT 'NORMAL' COMMENT '状态：NORMAL 正常 / DISABLED 停用（停用后禁发新帖，旧帖可见）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_board_code (code, deleted),
    KEY idx_forum_board_sort (status, sort_order, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '论坛版块';

CREATE TABLE IF NOT EXISTS forum_topic (
    id                  BIGINT       NOT NULL            COMMENT '主键（雪花 ID）',
    board_id            BIGINT       NOT NULL            COMMENT '版块 ID',
    title               VARCHAR(200) NOT NULL            COMMENT '标题',
    body                TEXT         NOT NULL            COMMENT '正文（纯文本，换行分段）',
    tags                VARCHAR(300) NULL                COMMENT '标签，逗号分隔',
    author_user_id      BIGINT       NOT NULL            COMMENT '发帖人 sys_user.id（JWT 载荷，跨库逻辑引用，不建外键）',
    author_name         VARCHAR(50)  NOT NULL            COMMENT '发帖人姓名快照（发帖时从 JWT realName 冻结，跨库零 JOIN）',
    author_group        VARCHAR(100) NULL                COMMENT '发帖人组织名快照（写入时冻结，见 forum_user 口径）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'NORMAL' COMMENT '状态机：NORMAL 正常 / LOCKED 锁定禁回 / HIDDEN 隐藏（HIDDEN 恢复一律回 NORMAL）',
    pinned              TINYINT      NOT NULL DEFAULT 0  COMMENT '置顶：1 是 0 否（标志位，不进状态机；DTO 出入参布尔化）',
    reply_count         INT          NOT NULL DEFAULT 0  COMMENT '回复数（回帖事务内维护）',
    view_count          INT          NOT NULL DEFAULT 0  COMMENT '浏览数（详情接口 +1，写放大注：热点上量后再异步化）',
    like_count          INT          NOT NULL DEFAULT 0  COMMENT '点赞数',
    collect_count       INT          NOT NULL DEFAULT 0  COMMENT '收藏数',
    last_reply_at       DATETIME     NULL                COMMENT '最后活跃时间（排序键；发帖时 = created_at，回帖时刷新）',
    last_reply_user_id  BIGINT       NULL                COMMENT '最后回复人',
    last_reply_by_name  VARCHAR(50)  NULL                COMMENT '最后回复人姓名快照',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除（作者删自己帖，或管理员删）',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '创建人（AuditMetaObjectHandler 暂不自动填）',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人（同上）',
    PRIMARY KEY (id),
    KEY idx_forum_topic_board (board_id, deleted, status, last_reply_at),
    KEY idx_forum_topic_author (author_user_id, deleted),
    KEY idx_forum_topic_hot (status, deleted, last_reply_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '论坛主题（帖子）';

CREATE TABLE IF NOT EXISTS forum_reply (
    id                  BIGINT       NOT NULL            COMMENT '主键（雪花 ID）',
    topic_id            BIGINT       NOT NULL            COMMENT '所属主题 ID',
    floor_no            INT          NOT NULL            COMMENT '楼层号（主题内唯一；并发撞 uk_forum_reply_floor 重试取号）',
    content             TEXT         NOT NULL            COMMENT '回复内容（纯文本）',
    quote_reply_id      BIGINT       NULL                COMMENT '引用楼层 ID，可空',
    quote_floor_no      INT          NULL                COMMENT '引用楼层号（冗余，免二次查库拼引用条）',
    quote_author_name   VARCHAR(50)  NULL                COMMENT '被引用人姓名快照',
    quote_digest        VARCHAR(200) NULL                COMMENT '被引用内容摘要（引用条展示）',
    author_user_id      BIGINT       NOT NULL            COMMENT '回帖人 sys_user.id',
    author_name         VARCHAR(50)  NOT NULL            COMMENT '回帖人姓名快照',
    author_group        VARCHAR(100) NULL                COMMENT '回帖人组织名快照',
    like_count          INT          NOT NULL DEFAULT 0  COMMENT '点赞数',
    status              VARCHAR(32)  NOT NULL DEFAULT 'NORMAL' COMMENT '状态机：NORMAL 正常 / HIDDEN 隐藏（占位显示）',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除（作者删自己楼层，或管理员删）',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_reply_floor (topic_id, floor_no),
    KEY idx_forum_reply_topic (topic_id, deleted, status, floor_no),
    KEY idx_forum_reply_author (author_user_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '论坛楼层（回复）';

CREATE TABLE IF NOT EXISTS forum_topic_like (
    id          BIGINT       NOT NULL                    COMMENT '主键（实体显式 @TableId(type = IdType.ASSIGN_ID)）',
    topic_id    BIGINT       NOT NULL                    COMMENT '主题 ID',
    user_id     BIGINT       NOT NULL                    COMMENT '点赞人 sys_user.id',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_topic_like (topic_id, user_id),
    KEY idx_forum_topic_like_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '主题点赞（硬删）';

CREATE TABLE IF NOT EXISTS forum_reply_like (
    id          BIGINT       NOT NULL                    COMMENT '主键（实体显式 @TableId(type = IdType.ASSIGN_ID)）',
    reply_id    BIGINT       NOT NULL                    COMMENT '楼层 ID',
    user_id     BIGINT       NOT NULL                    COMMENT '点赞人 sys_user.id',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_reply_like (reply_id, user_id),
    KEY idx_forum_reply_like_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '楼层点赞（硬删）';

CREATE TABLE IF NOT EXISTS forum_topic_collect (
    id          BIGINT       NOT NULL                    COMMENT '主键（实体显式 @TableId(type = IdType.ASSIGN_ID)）',
    topic_id    BIGINT       NOT NULL                    COMMENT '主题 ID',
    user_id     BIGINT       NOT NULL                    COMMENT '收藏人 sys_user.id',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_topic_collect (topic_id, user_id),
    KEY idx_forum_topic_collect_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '主题收藏（硬删）';

-- ############################################################################
-- 四、论坛用户快照（1 张）：memberCount 数据源 + author_group 冻结来源
--     生命周期：首次写操作触发 insert；写操作时 JWT orgId 与快照 org_id 不一致
--     则调管理侧 GET /api/auth/my-profile 换名刷新；无 deleted；语义=在论坛
--     有过写操作的账号数。org_name 展示"写入时"组织名（历史快照，组织改名不回刷）。
-- ############################################################################

CREATE TABLE IF NOT EXISTS forum_user (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    user_id         BIGINT       NOT NULL                 COMMENT 'sys_user.id（JWT 载荷，跨库逻辑引用）',
    real_name       VARCHAR(50)  NOT NULL                 COMMENT '姓名快照（首见时从 JWT realName 冻结）',
    org_id          BIGINT       NULL                     COMMENT '组织 ID 快照（与 JWT 比对，变化则触发换名）',
    org_name        VARCHAR(100) NULL                     COMMENT '组织名快照（写入时冻结；经 my-profile 接口换名）',
    first_seen_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次写操作时间',
    last_active_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后写操作时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '论坛用户快照';
