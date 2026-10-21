-- ============================================================================
-- VIOManager 数据库建表脚本（完整合集）
-- 50 张表，可一次性执行。改表结构需组长批准后直接修改本文件 docs/db/initsql.sql，
-- 全项目仅此一份表结构定义，避免漂移。
-- 技术交流域三张表已于 2026-10-15 迁至论坛独立库 vioforum
-- （建表脚本：backend/vio-forum/src/main/resources/db/migration/V1__forum_init.sql）。
-- 执行： mysql -uroot -p < 数据库建表脚本合集.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `viomanager`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `viomanager`;

SET FOREIGN_KEY_CHECKS = 0;

-- ############################################################################
-- 一、身份域 + 档案域 + 学期与文件域（10 张）
-- ############################################################################

CREATE TABLE IF NOT EXISTS sys_org (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    parent_id       BIGINT       NULL     DEFAULT 0      COMMENT '上级组织 ID，顶层的父为 0',
    name            VARCHAR(100) NOT NULL                COMMENT '组织名称',
    code            VARCHAR(64)  NULL                    COMMENT '组织编码，同一父节点下唯一，用于 Excel 导入匹配',
    org_type        VARCHAR(32)  NOT NULL                COMMENT '组织类型：COLLEGE 学院 / DEPARTMENT 部门 / CLASS 班级 / CLUB 社团部门 / SUB_ORG 分子组织 / OTHER 其他',
    is_sub_org      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否分子组织：1 是 0 否。排班与绩效按此维度隔离',
    manager_id      BIGINT       NULL                    COMMENT '该组织的部门管理员人员 ID',
    leader_name     VARCHAR(50)  NULL                    COMMENT '负责人姓名（仅展示用，可无账号）',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '同级排序号',
    path            VARCHAR(500) NULL                    COMMENT '祖先路径，形如 /0/1/12/，用于子树查询',
    level           INT          NOT NULL DEFAULT 1      COMMENT '层级，顶层为 1',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 0 停用',
    remark          VARCHAR(500) NULL                    COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人 sys_user.id',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人 sys_user.id',
    PRIMARY KEY (id),
    KEY idx_org_parent (parent_id, deleted),
    KEY idx_org_path (path(191)),
    KEY idx_org_type (org_type, is_sub_org, deleted),
    KEY idx_org_manager (manager_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '组织架构（树形，含分子组织）';

CREATE TABLE IF NOT EXISTS member (
    id                  BIGINT       NOT NULL            COMMENT '主键（雪花 ID）',
    name                VARCHAR(50)  NOT NULL            COMMENT '姓名（必填）',
    student_no          VARCHAR(32)  NULL                COMMENT '学号。可空、不可重复、不可修改',
    job_no              VARCHAR(32)  NULL                COMMENT '学工号。与学号分别独立，登录名默认取此值',
    phone               VARCHAR(20)  NULL                COMMENT '手机号',
    college             VARCHAR(100) NULL                COMMENT '学院',
    major               VARCHAR(100) NULL                COMMENT '专业',
    class_name          VARCHAR(100) NULL                COMMENT '班级',
    gender              VARCHAR(8)   NULL                COMMENT '性别：MALE 男 / FEMALE 女 / UNKNOWN 未知',
    nation              VARCHAR(32)  NULL                COMMENT '民族',
    id_card             VARCHAR(255) NULL                COMMENT '身份证号密文（AES），禁止明文入库',
    id_card_last4       VARCHAR(4)   NULL                COMMENT '身份证号后 4 位，供列表脱敏展示，避免解密',
    email               VARCHAR(100) NULL                COMMENT '邮箱',
    photo_file_id       BIGINT       NULL                COMMENT '证件照，引用 file_object.id（存 MinIO）',
    signature_file_id   BIGINT       NULL                COMMENT '电子签名，引用 file_object.id（存 MinIO）',
    org_id              BIGINT       NULL                COMMENT '所属组织（分子组织）ID',
    member_type         VARCHAR(32)  NOT NULL DEFAULT 'STUDENT' COMMENT '类型：STUDENT 学生 / TEACHER 教师 / OTHER 其他',
    status              TINYINT      NOT NULL DEFAULT 1  COMMENT '状态：1 在册 0 离册',
    has_account         TINYINT      NOT NULL DEFAULT 0  COMMENT '是否已绑定登录账号：1 是 0 否',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删 1 已删',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_student_no (student_no, deleted),
    UNIQUE KEY uk_member_job_no (job_no, deleted),
    KEY idx_member_org (org_id, deleted),
    KEY idx_member_type (member_type, status),
    KEY idx_member_name (name),
    KEY idx_member_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '人员档案';

CREATE TABLE IF NOT EXISTS sys_user (
    id                  BIGINT       NOT NULL            COMMENT '主键（雪花 ID）',
    member_id           BIGINT       NULL                COMMENT '关联人员档案 ID；纯管理账号可为空',
    login_name          VARCHAR(64)  NOT NULL            COMMENT '登录名。默认 = 学工号，可修改',
    password            VARCHAR(100) NOT NULL            COMMENT '密码，BCrypt 加密',
    role                VARCHAR(32)  NOT NULL            COMMENT '角色：SUPER_ADMIN / DEPT_ADMIN / STUDENT',
    org_id              BIGINT       NULL                COMMENT '所属组织 ID，部门管理员的数据范围根节点',
    real_name           VARCHAR(50)  NULL                COMMENT '姓名冗余，避免每次展示都关联 member',
    default_password    TINYINT      NOT NULL DEFAULT 1  COMMENT '是否仍为初始密码：1 是（前端弹改密提示）0 否',
    status              TINYINT      NOT NULL DEFAULT 1  COMMENT '状态：1 启用 0 停用',
    last_login_at       DATETIME     NULL                COMMENT '最后登录时间',
    last_login_ip       VARCHAR(64)  NULL                COMMENT '最后登录 IP',
    pwd_updated_at      DATETIME     NULL                COMMENT '密码最后修改时间',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删 1 已删',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_login_name (login_name, deleted),
    KEY idx_user_member (member_id),
    KEY idx_user_role_org (role, org_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '登录账号';

CREATE TABLE IF NOT EXISTS sys_login_log (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    user_id         BIGINT       NULL                    COMMENT '用户 ID，登录名不存在时为空',
    login_name      VARCHAR(64)  NOT NULL                COMMENT '尝试登录的登录名',
    login_type      VARCHAR(32)  NOT NULL DEFAULT 'PASSWORD' COMMENT '登录方式：PASSWORD 账密',
    success         TINYINT      NOT NULL                COMMENT '是否成功：1 成功 0 失败',
    fail_reason     VARCHAR(200) NULL                    COMMENT '失败原因',
    ip              VARCHAR(64)  NULL                    COMMENT '客户端 IP',
    user_agent      VARCHAR(300) NULL                    COMMENT 'User-Agent',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_login_log_user (user_id, created_at),
    KEY idx_login_log_name (login_name, created_at),
    KEY idx_login_log_time (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '登录日志';

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id              BIGINT       NOT NULL                COMMENT '主键',
    dict_code       VARCHAR(64)  NOT NULL                COMMENT '字典编码，如 SKILL / MATERIAL_TYPE',
    dict_name       VARCHAR(100) NOT NULL                COMMENT '字典名称',
    remark          VARCHAR(500) NULL                    COMMENT '说明',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type_code (dict_code, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典类型';

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id              BIGINT       NOT NULL                COMMENT '主键',
    dict_code       VARCHAR(64)  NOT NULL                COMMENT '所属字典编码',
    item_value      VARCHAR(64)  NOT NULL                COMMENT '字典值',
    item_label      VARCHAR(100) NOT NULL                COMMENT '展示文案',
    source          VARCHAR(16)  NOT NULL DEFAULT 'PRESET' COMMENT '来源：PRESET 预设 / USER 用户补充',
    audit_status    VARCHAR(16)  NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：APPROVED 已入库 / PENDING 待审核 / REJECTED 已驳回',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 0 停用',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_item (dict_code, item_value, deleted),
    KEY idx_dict_item_code (dict_code, status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典项';

CREATE TABLE IF NOT EXISTS member_skill (
    id              BIGINT       NOT NULL                COMMENT '主键',
    member_id       BIGINT       NOT NULL                COMMENT '人员档案 ID',
    skill_value     VARCHAR(64)  NOT NULL                COMMENT '技能值，对应 sys_dict_item.item_value（SKILL 字典）',
    skill_label     VARCHAR(100) NOT NULL                COMMENT '技能展示名，冗余避免联表',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_skill (member_id, skill_value, deleted),
    KEY idx_member_skill_value (skill_value, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '人员技能标签';

CREATE TABLE IF NOT EXISTS sys_config (
    id              BIGINT       NOT NULL                COMMENT '主键',
    config_key      VARCHAR(100) NOT NULL                COMMENT '配置键',
    config_value    VARCHAR(1000) NOT NULL               COMMENT '配置值',
    config_name     VARCHAR(200) NULL                    COMMENT '配置说明',
    editable        TINYINT      NOT NULL DEFAULT 1      COMMENT '是否允许界面修改：1 可改 0 只读',
    remark          VARCHAR(500) NULL                    COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统配置';

CREATE TABLE IF NOT EXISTS semester (
    id              BIGINT       NOT NULL                COMMENT '主键',
    name            VARCHAR(100) NOT NULL                COMMENT '学期名称，不可重复，如 2026-2027学年第一学期',
    start_date      DATE         NULL                    COMMENT '开始日期',
    end_date        DATE         NULL                    COMMENT '结束日期',
    status          VARCHAR(32)  NOT NULL DEFAULT 'PREPARING' COMMENT '状态：PREPARING 未开始 / ACTIVE 进行中 / ARCHIVED 已归档',
    is_current      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否当前学期：1 是 0 否，全平台仅一个为 1',
    total_weeks     INT          NOT NULL DEFAULT 20     COMMENT '教学周数，排班与绩效周次的取值范围',
    archived_at     DATETIME     NULL                    COMMENT '归档时间',
    remark          VARCHAR(500) NULL                    COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_semester_name (name, deleted),
    KEY idx_semester_current (is_current, status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学期';

CREATE TABLE IF NOT EXISTS file_object (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    object_key          VARCHAR(500) NOT NULL            COMMENT 'MinIO 对象 key，形如 member/photo/2026/10/07/uuid.jpg',
    original_name       VARCHAR(300) NOT NULL            COMMENT '原始文件名，下载时回显',
    file_ext            VARCHAR(32)  NULL                COMMENT '扩展名（小写，不含点）',
    content_type        VARCHAR(150) NULL                COMMENT 'MIME 类型',
    file_size           BIGINT       NOT NULL DEFAULT 0  COMMENT '字节数',
    sha256              VARCHAR(64)  NULL                COMMENT '文件 SHA-256，用于秒传与去重',
    biz_type            VARCHAR(32)  NOT NULL            COMMENT '业务类型：MEMBER_PHOTO / MEMBER_SIGNATURE / MATERIAL / ACHIEVEMENT / PROJECT',
    biz_id              BIGINT       NULL                COMMENT '关联业务 ID',
    scan_status         VARCHAR(32)  NOT NULL DEFAULT 'SKIPPED' COMMENT '病毒检测：PENDING 待检 / CLEAN 通过 / INFECTED 命中 / SKIPPED 未启用 / UNAVAILABLE 扫描服务不可用',
    scan_signature      VARCHAR(200) NULL                COMMENT '命中的病毒特征名',
    scan_at             DATETIME     NULL                COMMENT '检测时间',
    download_count      INT          NOT NULL DEFAULT 0  COMMENT '累计下载次数',
    org_id              BIGINT       NULL                COMMENT '所属组织，供数据范围过滤',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '上传人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_object_key (object_key),
    KEY idx_file_biz (biz_type, biz_id, deleted),
    KEY idx_file_org (org_id, deleted),
    KEY idx_file_sha256 (sha256)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件对象（MinIO 引用）';

-- ############################################################################
-- 二、赛事域（7 张）
-- ############################################################################

CREATE TABLE IF NOT EXISTS contest_template (
    id              BIGINT       NOT NULL                COMMENT '主键',
    name            VARCHAR(200) NOT NULL                COMMENT '模板名称',
    code            VARCHAR(64)  NULL                    COMMENT '模板编码',
    category        VARCHAR(64)  NULL                    COMMENT '赛事类别，如 电控 / 建模 / 程序设计',
    description     VARCHAR(1000) NULL                   COMMENT '模板说明',
    default_rules   JSON         NULL                    COMMENT '默认规则 JSON：{"memberLimit":5,"allowRepeatJoin":false,...}',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 0 停用',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_name (name, deleted),
    KEY idx_template_status (status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '比赛模板';

CREATE TABLE IF NOT EXISTS contest (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    name                VARCHAR(200) NOT NULL            COMMENT '比赛名称',
    template_id         BIGINT       NULL                COMMENT '来源模板 ID',
    semester_id         BIGINT       NOT NULL            COMMENT '所属学期 ID',
    org_id              BIGINT       NULL                COMMENT '归属组织（部门管理员数据范围依据）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布 / REGISTERING 报名中 / RUNNING 进行中 / FINISHED 已结束 / ARCHIVED 已归档',
    register_start_at   DATETIME     NULL                COMMENT '报名开始时间',
    register_end_at     DATETIME     NULL                COMMENT '报名截止时间',
    contest_start_at    DATETIME     NULL                COMMENT '比赛开始时间',
    contest_end_at      DATETIME     NULL                COMMENT '比赛结束时间',
    contact_name        VARCHAR(50)  NULL                COMMENT '联系人姓名',
    contact_phone       VARCHAR(20)  NULL                COMMENT '联系人电话',
    description         TEXT         NULL                COMMENT '比赛描述',
    override_rules      JSON         NULL                COMMENT '比赛级规则覆盖 JSON，仅存被覆盖的键',
    source_type         VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 手工 / CRAWLER 爬虫 / RSS / CRAWLER_POOL 待处理池转入',
    source_ref          VARCHAR(500) NULL                COMMENT '外部来源标识（URL 或来源 ID），用于去重',
    archived_at         DATETIME     NULL                COMMENT '归档时间',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_contest_semester (semester_id, status, deleted),
    KEY idx_contest_org (org_id, deleted),
    KEY idx_contest_status_time (status, register_start_at),
    KEY idx_contest_source (source_type, source_ref(191))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '比赛实例';

CREATE TABLE IF NOT EXISTS contest_track (
    id              BIGINT       NOT NULL                COMMENT '主键',
    contest_id      BIGINT       NOT NULL                COMMENT '所属比赛 ID',
    name            VARCHAR(200) NOT NULL                COMMENT '赛道名称',
    code            VARCHAR(64)  NULL                    COMMENT '赛道编码',
    override_rules  JSON         NULL                    COMMENT '赛道级规则覆盖 JSON',
    description     VARCHAR(1000) NULL                   COMMENT '赛道说明',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 0 停用',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_track (contest_id, name, deleted),
    KEY idx_track_contest (contest_id, status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '比赛赛道';

CREATE TABLE IF NOT EXISTS contest_source (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    title               VARCHAR(300) NOT NULL            COMMENT '抓取到的比赛标题',
    source_type         VARCHAR(32)  NOT NULL            COMMENT '来源类型：CRAWLER / RSS / MANUAL',
    source_url          VARCHAR(500) NULL                COMMENT '来源地址',
    source_key          VARCHAR(200) NULL                COMMENT '来源侧唯一标识，用于去重',
    dedup_key           VARCHAR(300) NOT NULL            COMMENT '去重键：source_key + title + contest_time 的哈希',
    raw_content         TEXT         NULL                COMMENT '原始抓取内容，供管理员判断',
    contest_time        DATETIME     NULL                COMMENT '解析出的比赛时间',
    parsed_rules        JSON         NULL                COMMENT '解析出的规则线索',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待处理 / CONFIRMED 已转为比赛 / IGNORED 已忽略',
    confirmed_contest_id BIGINT      NULL                COMMENT '确认后生成的比赛 ID',
    fetched_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    handled_at          DATETIME     NULL                COMMENT '处理时间',
    handled_by          BIGINT       NULL                COMMENT '处理人',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contest_source_dedup (dedup_key),
    KEY idx_contest_source_status (status, fetched_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '外部比赛待处理池';

CREATE TABLE IF NOT EXISTS team (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    name                VARCHAR(200) NOT NULL            COMMENT '队名，同比赛下唯一',
    project_name        VARCHAR(200) NULL                COMMENT '项目名',
    contest_id          BIGINT       NOT NULL            COMMENT '所属比赛 ID',
    track_id            BIGINT       NULL                COMMENT '所属赛道 ID',
    semester_id         BIGINT       NOT NULL            COMMENT '所属学期 ID',
    org_id              BIGINT       NOT NULL            COMMENT '所属组织（部门管理员数据范围依据）',
    leader_member_id    BIGINT       NULL                COMMENT '队长人员 ID，冗余便于快速查询与唯一性校验',
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 正常 / DISSOLVED 已解散',
    material_status     VARCHAR(32)  NOT NULL DEFAULT 'NOT_STARTED' COMMENT '材料状态：NOT_STARTED 未开始 / PENDING 材料待补 / SUBMITTED 材料已交',
    material_complete_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '材料齐全率百分数，如 66.67',
    dissolved_at        DATETIME     NULL                COMMENT '解散时间',
    dissolve_reason     VARCHAR(500) NULL                COMMENT '解散原因',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_name_contest (contest_id, name, deleted),
    KEY idx_team_contest (contest_id, status, deleted),
    KEY idx_team_semester (semester_id, deleted),
    KEY idx_team_org (org_id, deleted),
    KEY idx_team_leader (leader_member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '赛队';

CREATE TABLE IF NOT EXISTS team_member (
    id              BIGINT       NOT NULL                COMMENT '主键',
    team_id         BIGINT       NOT NULL                COMMENT '赛队 ID',
    member_id       BIGINT       NOT NULL                COMMENT '人员档案 ID',
    member_name     VARCHAR(50)  NOT NULL                COMMENT '人员姓名冗余',
    member_org_id   BIGINT       NULL                    COMMENT '人员所属组织冗余',
    is_leader       TINYINT      NOT NULL DEFAULT 0      COMMENT '是否队长：1 是 0 否',
    status          VARCHAR(32)  NOT NULL DEFAULT 'UNCONFIRMED' COMMENT '状态：UNCONFIRMED 未确认(橙) / CONFIRMED 已确认(绿) / REJECTED 已拒绝(红) / EXITED 已退出 / REMOVED 已被移除',
    joined_at       DATETIME     NULL                    COMMENT '加入时间（确认时写入）',
    left_at         DATETIME     NULL                    COMMENT '离开时间',
    left_reason     VARCHAR(500) NULL                    COMMENT '离开原因',
    join_snapshot   JSON         NULL                    COMMENT '加入时的人数与规则快照',
    remark          VARCHAR(500) NULL                    COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_member (team_id, member_id, deleted),
    KEY idx_team_member_team (team_id, status, deleted),
    KEY idx_team_member_member (member_id, status, deleted),
    KEY idx_team_member_leader (team_id, is_leader)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '赛队成员';

CREATE TABLE IF NOT EXISTS team_advisor (
    id              BIGINT       NOT NULL                COMMENT '主键',
    team_id         BIGINT       NOT NULL                COMMENT '赛队 ID',
    member_id       BIGINT       NOT NULL                COMMENT '指导教师人员档案 ID（member_type=TEACHER）',
    advisor_name    VARCHAR(50)  NOT NULL                COMMENT '教师姓名冗余',
    title           VARCHAR(100) NULL                    COMMENT '职称，如 教授 / 副教授',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 在任 / REMOVED 已移除',
    removed_at      DATETIME     NULL                    COMMENT '移除时间',
    remark          VARCHAR(500) NULL                    COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_advisor (team_id, member_id, deleted),
    KEY idx_team_advisor_team (team_id, status, deleted),
    KEY idx_team_advisor_member (member_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '赛队指导教师';

-- ############################################################################
-- 三、材料成果域 + 流程域（8 张）
-- ############################################################################

CREATE TABLE IF NOT EXISTS material_task (
    id              BIGINT       NOT NULL                COMMENT '主键',
    contest_id      BIGINT       NOT NULL                COMMENT '所属比赛 ID',
    semester_id     BIGINT       NOT NULL                COMMENT '所属学期 ID',
    org_id          BIGINT       NULL                    COMMENT '归属组织',
    name            VARCHAR(200) NOT NULL                COMMENT '任务名称，如「初赛材料收集」',
    description     VARCHAR(1000) NULL                   COMMENT '任务说明',
    deadline        DATETIME     NULL                    COMMENT '截止时间',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 进行中 / CLOSED 已关闭',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_material_task_contest (contest_id, status, deleted),
    KEY idx_material_task_semester (semester_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '材料收集任务';

CREATE TABLE IF NOT EXISTS material_requirement (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    task_id             BIGINT       NOT NULL            COMMENT '所属材料任务 ID',
    contest_id          BIGINT       NOT NULL            COMMENT '所属比赛 ID（冗余，便于校验赛队比赛匹配）',
    material_type       VARCHAR(64)  NOT NULL            COMMENT '材料类型：REGISTRATION_FORM 报名表 / PROJECT_PLAN 项目书 / COMMITMENT 承诺书 / ID_COPY 身份证复印件 / STUDENT_CARD_COPY 学生证复印件 / TEAM_PHOTO 团队合照 / DEFENSE_PPT 答辩PPT / RESEARCH_REPORT 研究报告 / SOURCE_CODE 源代码 / AWARD_CERT 获奖证书 / EVIDENCE 佐证材料 / OTHER 其他',
    name                VARCHAR(200) NOT NULL            COMMENT '要求名称，展示用',
    required_flag       TINYINT      NOT NULL DEFAULT 1  COMMENT '是否必传：1 必传 0 选传。必传项决定齐全率分母',
    allowed_formats     VARCHAR(200) NULL                COMMENT '允许格式，逗号分隔，如 pdf,docx,jpg；空表示不限',
    max_size_mb         INT          NULL                COMMENT '单文件大小上限（MB），空表示用全局配置',
    naming_rule         VARCHAR(300) NULL                COMMENT '命名规则（可选），前端校验提示用',
    system_generated    TINYINT      NOT NULL DEFAULT 0  COMMENT '是否系统自动追加：1 是（成果佐证材料）。系统项不参与管理端增删',
    sort_no             INT          NOT NULL DEFAULT 0  COMMENT '排序号',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_material_req_task (task_id, deleted),
    KEY idx_material_req_contest (contest_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '材料要求';

CREATE TABLE IF NOT EXISTS material_submission (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    requirement_id      BIGINT       NOT NULL            COMMENT '材料要求 ID',
    task_id             BIGINT       NOT NULL            COMMENT '材料任务 ID（冗余）',
    contest_id          BIGINT       NOT NULL            COMMENT '比赛 ID（冗余）',
    team_id             BIGINT       NOT NULL            COMMENT '赛队 ID',
    semester_id         BIGINT       NOT NULL            COMMENT '学期 ID（冗余，便于按学期归档）',
    org_id              BIGINT       NOT NULL            COMMENT '赛队所属组织（数据范围过滤依据）',
    file_id             BIGINT       NOT NULL            COMMENT '文件对象 ID',
    file_name           VARCHAR(300) NOT NULL            COMMENT '文件名冗余，列表展示免联表',
    file_size           BIGINT       NOT NULL DEFAULT 0  COMMENT '文件大小冗余',
    is_current          TINYINT      NOT NULL DEFAULT 1  COMMENT '是否当前生效版本：1 生效 0 已被覆盖',
    version_no          INT          NOT NULL DEFAULT 1  COMMENT '版本号，每次覆盖 +1',
    submitted_by        BIGINT       NOT NULL            COMMENT '提交人人员 ID（必须是队长或队员）',
    submitted_by_name   VARCHAR(50)  NULL                COMMENT '提交人姓名冗余',
    submitted_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    source_zip_name     VARCHAR(300) NULL                COMMENT '来源压缩包名（若通过 zip 批量上传）',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_submission_team (team_id, requirement_id, is_current, deleted),
    KEY idx_submission_contest (contest_id, deleted),
    KEY idx_submission_semester (semester_id, deleted),
    KEY idx_submission_org (org_id, deleted),
    KEY idx_submission_req (requirement_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '材料提交';

CREATE TABLE IF NOT EXISTS achievement (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    team_id             BIGINT       NULL                COMMENT '所属赛队 ID。个人成果可为空',
    contest_id          BIGINT       NULL                COMMENT '关联比赛 ID',
    semester_id         BIGINT       NOT NULL            COMMENT '所属学期 ID',
    org_id              BIGINT       NOT NULL            COMMENT '所属组织',
    owner_member_id     BIGINT       NOT NULL            COMMENT '成果归属人人员 ID（提交时为队长）',
    owner_name          VARCHAR(50)  NULL                COMMENT '归属人姓名冗余',
    achievement_type    VARCHAR(64)  NOT NULL            COMMENT '类型：AWARD 获奖 / RATING 评级 / QUALIFICATION 资质 / ACTIVITY 活动成果 / OTHER 其他',
    name                VARCHAR(300) NOT NULL            COMMENT '成果名称',
    level               VARCHAR(64)  NULL                COMMENT '级别：国家级 / 省级 / 市级 / 校级 / 院级 等',
    award_date          DATE         NULL                COMMENT '获奖/取得日期',
    description         TEXT         NULL                COMMENT '成果描述',
    evidence_file_id    BIGINT       NULL                COMMENT '佐证材料文件 ID（成果生效后计入材料必交项）',
    evidence_file_name  VARCHAR(300) NULL                COMMENT '佐证文件名冗余',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回',
    submit_channel      VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '提交渠道：USER 队长提交 / ADMIN 管理员录入',
    approved_at         DATETIME     NULL                COMMENT '审批通过时间',
    approved_by         BIGINT       NULL                COMMENT '审批人',
    reject_reason       VARCHAR(500) NULL                COMMENT '驳回原因',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_achievement_team (team_id, status, deleted),
    KEY idx_achievement_owner (owner_member_id, status, deleted),
    KEY idx_achievement_semester (semester_id, status, deleted),
    KEY idx_achievement_contest (contest_id, deleted),
    KEY idx_achievement_org (org_id, deleted),
    KEY idx_achievement_type (achievement_type, level)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '成果记录';

CREATE TABLE IF NOT EXISTS achievement_file (
    id              BIGINT       NOT NULL                COMMENT '主键',
    achievement_id  BIGINT       NOT NULL                COMMENT '成果 ID',
    file_id         BIGINT       NOT NULL                COMMENT '文件对象 ID',
    file_name       VARCHAR(300) NOT NULL                COMMENT '文件名冗余',
    file_type       VARCHAR(32)  NOT NULL DEFAULT 'EVIDENCE' COMMENT '附件类型：EVIDENCE 佐证材料 / ANNEX 附加材料',
    is_current      TINYINT      NOT NULL DEFAULT 1      COMMENT '是否当前生效版本',
    version_no      INT          NOT NULL DEFAULT 1      COMMENT '版本号',
    uploaded_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_achievement_file_biz (achievement_id, file_type, is_current, deleted),
    KEY idx_achievement_file_id (file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '成果附件';

CREATE TABLE IF NOT EXISTS approval (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    biz_type            VARCHAR(64)  NOT NULL            COMMENT '业务类型：TEAM_CREATE / TEAM_ADD_MEMBER / TEAM_REMOVE_MEMBER / TEAM_ADD_ADVISOR / TEAM_DISSOLVE / ACHIEVEMENT_SUBMIT / PROJECT_VIEW / MILESTONE_APPROVE / SEMESTER_SWITCH',
    biz_id              BIGINT       NULL                COMMENT '关联业务 ID，批准后据此创建/更新正式数据',
    biz_title           VARCHAR(300) NULL                COMMENT '业务标题，列表展示用（如队名 + 比赛名）',
    biz_payload         JSON         NULL                COMMENT '申请内容快照 JSON',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待处理 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消 / EXPIRED 已过期',
    applicant_id        BIGINT       NOT NULL            COMMENT '发起人人员 ID',
    applicant_name      VARCHAR(50)  NULL                COMMENT '发起人姓名冗余',
    target_member_id    BIGINT       NULL                COMMENT '目标人人员 ID（如被邀请入队的队员），需其同意/拒绝',
    target_member_name  VARCHAR(50)  NULL                COMMENT '目标人姓名冗余',
    approver_org_id     BIGINT       NOT NULL            COMMENT '审批责任组织 ID，决定哪个管理员收到待办',
    approver_id         BIGINT       NULL                COMMENT '实际审批人 sys_user.id',
    approver_name       VARCHAR(50)  NULL                COMMENT '审批人姓名冗余',
    team_id             BIGINT       NULL                COMMENT '关联赛队 ID（赛队类审批填，便于权限判定）',
    contest_id          BIGINT       NULL                COMMENT '关联比赛 ID（冗余，便于筛选）',
    semester_id         BIGINT       NULL                COMMENT '所属学期 ID',
    org_id              BIGINT       NULL                COMMENT '归属组织（数据范围过滤依据）',
    apply_reason        VARCHAR(1000) NULL               COMMENT '申请说明',
    handle_comment      VARCHAR(1000) NULL               COMMENT '处理意见',
    handled_at          DATETIME     NULL                COMMENT '处理时间',
    expire_at           DATETIME     NULL                COMMENT '过期时间，到期未处理自动转 EXPIRED',
    applicant_read      TINYINT      NOT NULL DEFAULT 0  COMMENT '发起人是否已读结果：1 已读 0 未读',
    target_read         TINYINT      NOT NULL DEFAULT 0  COMMENT '目标人是否已读邀请：1 已读 0 未读',
    approver_read       TINYINT      NOT NULL DEFAULT 0  COMMENT '审批人是否已读：1 已读 0 未读',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_approval_status_org (status, approver_org_id, deleted),
    KEY idx_approval_biz (biz_type, biz_id, status),
    KEY idx_approval_applicant (applicant_id, status, deleted),
    KEY idx_approval_target (target_member_id, status, deleted),
    KEY idx_approval_approver (approver_id, status, deleted),
    KEY idx_approval_team (team_id, status, deleted),
    KEY idx_approval_unread (applicant_id, applicant_read, target_member_id, target_read),
    KEY idx_approval_expire (status, expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批单';

CREATE TABLE IF NOT EXISTS approval_record (
    id              BIGINT       NOT NULL                COMMENT '主键',
    approval_id     BIGINT       NOT NULL                COMMENT '审批单 ID',
    action          VARCHAR(32)  NOT NULL                COMMENT '动作：SUBMIT 发起 / CANCEL 取消 / APPROVE 批准 / REJECT 驳回 / AGREE 同意 / REFUSE 拒绝 / EXPIRE 过期',
    operator_id     BIGINT       NULL                    COMMENT '操作人 sys_user.id',
    operator_name   VARCHAR(50)  NULL                    COMMENT '操作人姓名',
    operator_role   VARCHAR(32)  NULL                    COMMENT '操作人角色：APPLICANT 发起人 / TARGET 目标人 / APPROVER 审批人',
    comment_text    VARCHAR(1000) NULL                   COMMENT '意见 / 备注',
    from_status     VARCHAR(32)  NULL                    COMMENT '变更前状态',
    to_status       VARCHAR(32)  NULL                    COMMENT '变更后状态',
    ip              VARCHAR(64)  NULL                    COMMENT '操作 IP',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_approval_record (approval_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批操作流水';

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
-- 四、项目管理域（8 张）
-- ############################################################################

CREATE TABLE IF NOT EXISTS project (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    name                VARCHAR(200) NOT NULL            COMMENT '项目名称',
    description         TEXT         NULL                COMMENT '项目描述',
    owner_member_id     BIGINT       NOT NULL            COMMENT '项目所有人人员 ID',
    owner_name          VARCHAR(50)  NULL                COMMENT '项目所有人姓名冗余',
    contest_id          BIGINT       NULL                COMMENT '所属比赛 ID（可选挂载）',
    track_id            BIGINT       NULL                COMMENT '所属赛道 ID（可选）',
    semester_id         BIGINT       NOT NULL            COMMENT '所属学期 ID',
    org_id              BIGINT       NOT NULL            COMMENT '所属子组织 ID（数据范围与绩效隔离依据）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 进行中 / FINISHED 已结束 / ARCHIVED 已归档',
    milestone_anchor    DATETIME     NULL                COMMENT '比赛时间节点锚点日期',
    start_date          DATE         NULL                COMMENT '项目开始日期',
    end_date            DATE         NULL                COMMENT '项目结束日期',
    visibility          VARCHAR(32)  NOT NULL DEFAULT 'ORG' COMMENT '可见范围：PRIVATE 仅所有人 / ORG 本部门 / PUBLIC 全平台',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_name (semester_id, org_id, name, deleted),
    KEY idx_project_owner (owner_member_id, status, deleted),
    KEY idx_project_org (org_id, semester_id, deleted),
    KEY idx_project_contest (contest_id, deleted),
    KEY idx_project_status (status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目';

CREATE TABLE IF NOT EXISTS project_member (
    id              BIGINT       NOT NULL                COMMENT '主键',
    project_id      BIGINT       NOT NULL                COMMENT '项目 ID',
    member_id       BIGINT       NOT NULL                COMMENT '人员档案 ID',
    member_name     VARCHAR(50)  NOT NULL                COMMENT '姓名冗余',
    member_org_id   BIGINT       NULL                    COMMENT '所属组织冗余（跨部门成员识别）',
    project_role    VARCHAR(32)  NOT NULL DEFAULT 'MEMBER' COMMENT '项目角色：OWNER 所有人 / MEMBER 成员',
    join_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    remark          VARCHAR(500) NULL                    COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_member (project_id, member_id, deleted),
    KEY idx_project_member_member (member_id, deleted),
    KEY idx_project_member_role (project_id, project_role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目成员';

CREATE TABLE IF NOT EXISTS project_advisor (
    id              BIGINT       NOT NULL                COMMENT '主键',
    project_id      BIGINT       NOT NULL                COMMENT '项目 ID',
    member_id       BIGINT       NOT NULL                COMMENT '教师人员 ID',
    advisor_name    VARCHAR(50)  NOT NULL                COMMENT '教师姓名冗余',
    title           VARCHAR(100) NULL                    COMMENT '职称',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 在任 / REMOVED 已移除',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_advisor (project_id, member_id, deleted),
    KEY idx_project_advisor_member (member_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目指导教师';

CREATE TABLE IF NOT EXISTS team_project_ref (
    id              BIGINT       NOT NULL                COMMENT '主键',
    team_id         BIGINT       NOT NULL                COMMENT '赛队 ID',
    project_id      BIGINT       NOT NULL                COMMENT '项目 ID',
    contest_id      BIGINT       NOT NULL                COMMENT '比赛 ID（冗余）',
    track_id        BIGINT       NULL                    COMMENT '挂载时选择的赛道 ID',
    semester_id     BIGINT       NOT NULL                COMMENT '学期 ID（校验同项目同学期）',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 挂载中 / RELEASED 已解绑',
    attached_by     BIGINT       NOT NULL                COMMENT '挂载操作人人员 ID（队长）',
    attached_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '挂载时间',
    released_at     DATETIME     NULL                    COMMENT '解绑时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_project (team_id, project_id, track_id, deleted),
    KEY idx_team_project_team (team_id, status, deleted),
    KEY idx_team_project_project (project_id, status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '赛队挂载项目';

CREATE TABLE IF NOT EXISTS milestone_node (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    contest_id          BIGINT       NOT NULL            COMMENT '所属比赛 ID',
    semester_id         BIGINT       NOT NULL            COMMENT '所属学期 ID',
    org_id              BIGINT       NULL                COMMENT '归属组织',
    node_type           VARCHAR(64)  NOT NULL            COMMENT '节点类型：REGISTER_START 报名开始 / REGISTER_END 报名截止 / WORK_SUBMIT 作品提交 / OTHER 其他',
    name                VARCHAR(200) NOT NULL            COMMENT '节点名称',
    node_time           DATETIME     NOT NULL            COMMENT '节点时间',
    description         VARCHAR(1000) NULL               COMMENT '说明',
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回',
    approved_at         DATETIME     NULL                COMMENT '审核通过时间',
    approved_by         BIGINT       NULL                COMMENT '审核人',
    reject_reason       VARCHAR(500) NULL                COMMENT '驳回原因',
    synced              TINYINT      NOT NULL DEFAULT 0  COMMENT '是否已同步给挂载项目：1 已同步 0 未同步',
    synced_at           DATETIME     NULL                COMMENT '同步时间',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_milestone_contest (contest_id, status, deleted),
    KEY idx_milestone_sync (status, synced, deleted),
    KEY idx_milestone_time (node_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '比赛时间节点';

CREATE TABLE IF NOT EXISTS project_task (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    project_id          BIGINT       NOT NULL            COMMENT '项目 ID',
    milestone_node_id   BIGINT       NULL                COMMENT '来源时间节点 ID，手工任务为空',
    title               VARCHAR(300) NOT NULL            COMMENT '任务标题',
    description         VARCHAR(1000) NULL               COMMENT '任务说明',
    assignee_member_id  BIGINT       NULL                COMMENT '负责人人员 ID',
    assignee_name       VARCHAR(50)  NULL                COMMENT '负责人姓名冗余',
    due_date            DATETIME     NULL                COMMENT '截止时间',
    status              VARCHAR(32)  NOT NULL DEFAULT 'TODO' COMMENT '状态：TODO 未开始 / DOING 进行中 / DONE 已完成 / BLOCKED 受阻',
    priority            VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：HIGH / NORMAL / LOW',
    completed_at        DATETIME     NULL                COMMENT '完成时间',
    completed_by        BIGINT       NULL                COMMENT '完成人',
    sort_no             INT          NOT NULL DEFAULT 0  COMMENT '排序号',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_project_task_project (project_id, status, deleted),
    KEY idx_project_task_assignee (assignee_member_id, status, deleted),
    KEY idx_project_task_milestone (milestone_node_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目任务';

CREATE TABLE IF NOT EXISTS project_file (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    project_id          BIGINT       NOT NULL            COMMENT '项目 ID',
    file_id             BIGINT       NOT NULL            COMMENT '文件对象 ID（file_object 中已含 scan_status）',
    file_name           VARCHAR(300) NOT NULL            COMMENT '文件名冗余',
    file_size           BIGINT       NOT NULL DEFAULT 0  COMMENT '文件大小冗余',
    file_category       VARCHAR(32)  NOT NULL DEFAULT 'DOC' COMMENT '文件分类：DOC 文档 / IMAGE 图片 / CODE 代码 / OTHER 其他',
    scan_status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '检测状态：CLEAN 通过 / INFECTED 命中 / UNAVAILABLE 服务不可用被拒',
    scan_signature      VARCHAR(200) NULL                COMMENT '命中特征名（被拒时记录）',
    uploaded_by         BIGINT       NOT NULL            COMMENT '上传人人员 ID',
    uploaded_by_name    VARCHAR(50)  NULL                COMMENT '上传人姓名冗余',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_project_file_project (project_id, deleted),
    KEY idx_project_file_scan (scan_status, created_at),
    KEY idx_project_file_id (file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目文件';

CREATE TABLE IF NOT EXISTS project_view_grant (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    project_id          BIGINT       NOT NULL            COMMENT '项目 ID',
    applicant_id        BIGINT       NOT NULL            COMMENT '申请人人员 ID',
    applicant_name      VARCHAR(50)  NULL                COMMENT '申请人姓名冗余',
    applicant_org_id    BIGINT       NULL                COMMENT '申请人所属组织（跨部门判定）',
    approval_id         BIGINT       NULL                COMMENT '关联审批单 ID',
    grant_type          VARCHAR(32)  NOT NULL DEFAULT 'ONCE' COMMENT '授权类型：ONCE 一次性 / LIMITED 限时',
    expire_at           DATETIME     NULL                COMMENT '限时授权的到期时间；一次性授权用掉后置为 used',
    used_count          INT          NOT NULL DEFAULT 0  COMMENT '已使用次数',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审批 / ACTIVE 生效中 / EXPIRED 已失效 / REVOKED 已撤销',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_pvg_project (project_id, status, deleted),
    KEY idx_pvg_applicant (applicant_id, status, deleted),
    KEY idx_pvg_expire (status, expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目查看授权';

-- ############################################################################
-- 五、排班调度域（5 张）
-- ############################################################################

CREATE TABLE IF NOT EXISTS timetable_import (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    semester_id         BIGINT       NULL                COMMENT '归一化后关联的学期 ID，归一化失败时为空待人工指定',
    semester_raw_name   VARCHAR(200) NULL                COMMENT '导入文件中的原始学期名称',
    file_id             BIGINT       NULL                COMMENT '原始课表文件 ID',
    file_name           VARCHAR(300) NULL                COMMENT '原始文件名',
    org_id              BIGINT       NULL                COMMENT '上传操作人所属组织',
    total_rows          INT          NOT NULL DEFAULT 0  COMMENT '总行数',
    success_rows        INT          NOT NULL DEFAULT 0  COMMENT '成功行数',
    failed_rows         INT          NOT NULL DEFAULT 0  COMMENT '失败行数',
    error_detail        TEXT         NULL                COMMENT '每行错误原因 JSON',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待归一化 / MAPPING 待确认映射 / COMPLETED 已完成 / FAILED 失败',
    imported_by         BIGINT       NULL                COMMENT '导入人 sys_user.id',
    imported_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_tt_import_semester (semester_id, status, deleted),
    KEY idx_tt_import_status (status, imported_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课表导入批次';

CREATE TABLE IF NOT EXISTS course_timetable (
    id              BIGINT       NOT NULL                COMMENT '主键',
    import_id       BIGINT       NULL                    COMMENT '来源导入批次 ID',
    semester_id     BIGINT       NOT NULL                COMMENT '学期 ID',
    class_name      VARCHAR(150) NOT NULL                COMMENT '课表班级名称（原始，未归一化）',
    class_key       VARCHAR(150) NOT NULL                COMMENT '归一化后的班级键，用于与人员档案班级匹配',
    week_no         INT          NOT NULL                COMMENT '周次，1 起',
    weekday         TINYINT      NOT NULL                COMMENT '星期：1 周一 ... 7 周日',
    slot_index      TINYINT      NOT NULL                COMMENT '节次索引：1-5 对应五大节',
    slot_name       VARCHAR(50)  NULL                    COMMENT '节次名称，如「第1-2节」',
    course_name     VARCHAR(200) NULL                    COMMENT '课程名称',
    teacher_name    VARCHAR(50)  NULL                    COMMENT '任课教师',
    classroom       VARCHAR(100) NULL                    COMMENT '上课地点',
    week_type       VARCHAR(16)  NOT NULL DEFAULT 'ALL'  COMMENT '周次类型：ALL 每周 / ODD 单周 / EVEN 双周',
    org_id          BIGINT       NULL                    COMMENT '所属组织',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_course_semester_class (semester_id, class_key, week_no, deleted),
    KEY idx_course_slot (semester_id, week_no, weekday, slot_index, deleted),
    KEY idx_course_import (import_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课表记录';

CREATE TABLE IF NOT EXISTS class_mapping (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    semester_id         BIGINT       NOT NULL            COMMENT '学期 ID',
    import_id           BIGINT       NULL                COMMENT '来源导入批次 ID',
    class_key           VARCHAR(150) NOT NULL            COMMENT '课表班级归一化键',
    class_name_raw      VARCHAR(150) NOT NULL            COMMENT '课表班级原始名',
    member_class_name   VARCHAR(150) NULL                COMMENT '档案侧班级名（member.class_name）',
    grade               VARCHAR(20)  NULL                COMMENT '年级，如 2022',
    grade_source        VARCHAR(16)  NULL                COMMENT '年级来源：STUDENT_NO 学号推导 / CLASS_NAME 班级名兜底',
    confirmed           TINYINT      NOT NULL DEFAULT 0  COMMENT '是否已确认映射：1 已确认（参与空课统计）0 未确认',
    confirmed_by        BIGINT       NULL                COMMENT '确认人',
    confirmed_at        DATETIME     NULL                COMMENT '确认时间',
    org_id              BIGINT       NULL                COMMENT '所属组织',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_mapping (semester_id, class_key, deleted),
    KEY idx_class_mapping_confirmed (semester_id, confirmed, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课表班级与档案班级映射';

CREATE TABLE IF NOT EXISTS schedule (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    semester_id         BIGINT       NOT NULL            COMMENT '学期 ID',
    week_no             INT          NOT NULL            COMMENT '周次',
    sub_org_id          BIGINT       NOT NULL            COMMENT '子组织 ID（排班隔离维度）',
    member_id           BIGINT       NOT NULL            COMMENT '排班人员 ID',
    member_name         VARCHAR(50)  NULL                COMMENT '人员姓名冗余',
    member_class_name   VARCHAR(150) NULL                COMMENT '人员班级冗余（展示用）',
    weekday             TINYINT      NOT NULL            COMMENT '星期：1-7',
    slot_index          TINYINT      NOT NULL            COMMENT '节次索引：1-5',
    slot_name           VARCHAR(50)  NULL                COMMENT '节次名称，如「第1-2节」',
    duty_name           VARCHAR(100) NULL                COMMENT '值班岗位/内容，如「机房值班」',
    course_conflict     TINYINT      NOT NULL DEFAULT 0  COMMENT '是否与课表冲突：1 有课冲突（管理员强制保存）0 空课',
    conflict_note       VARCHAR(300) NULL                COMMENT '冲突提示，如「第3-4节有课：高等数学」',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED 已排 / CANCELLED 已取消',
    org_id              BIGINT       NOT NULL            COMMENT '归属组织（数据范围过滤依据）',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_conflict (semester_id, week_no, sub_org_id, member_id, slot_index, deleted),
    KEY idx_schedule_member (member_id, semester_id, week_no, deleted),
    KEY idx_schedule_suborg (sub_org_id, semester_id, week_no, deleted),
    KEY idx_schedule_org (org_id, semester_id, deleted),
    KEY idx_schedule_slot (semester_id, week_no, sub_org_id, weekday, slot_index, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '排班（学期+周次+子组织三维）';

CREATE TABLE IF NOT EXISTS schedule_rule (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    sub_org_id          BIGINT       NOT NULL            COMMENT '子组织 ID',
    semester_id         BIGINT       NULL                COMMENT '学期 ID，为空表示对该子组织所有学期生效',
    weekday             TINYINT      NOT NULL            COMMENT '可排班星期：1-7',
    slot_index          TINYINT      NOT NULL            COMMENT '可排班节次：1-5',
    min_person          INT          NOT NULL DEFAULT 1  COMMENT '该时段最少人数',
    max_person          INT          NOT NULL DEFAULT 5  COMMENT '该时段最多人数',
    enabled             TINYINT      NOT NULL DEFAULT 1  COMMENT '是否启用该时段',
    remark              VARCHAR(500) NULL                COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_rule (sub_org_id, semester_id, weekday, slot_index, deleted),
    KEY idx_schedule_rule_org (sub_org_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '子组织排班规则';

-- ############################################################################
-- 六、知识库域（3 张）（技术交流域已迁论坛库 vioforum，见文件头注）
-- ############################################################################

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
    file_name           VARCHAR(300) NULL                COMMENT '附件原始文件名（FILE 形态冗余，列表展示与下载兜底）',
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
    publisher_name      VARCHAR(64)  NULL                 COMMENT '发布人姓名（JWT realName 快照）',
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

-- ############################################################################
-- 七、绩效统计域 + AI 助手域 + 系统运行域（8 张）
-- ############################################################################

CREATE TABLE IF NOT EXISTS performance_rule (
    id              BIGINT       NOT NULL                COMMENT '主键',
    sub_org_id      BIGINT       NOT NULL                COMMENT '子组织 ID',
    semester_id     BIGINT       NULL                    COMMENT '学期 ID，为空表示对所有学期生效',
    dimension_key   VARCHAR(64)  NOT NULL                COMMENT '打分维度键，如 TECH_OUTPUT / COLLABORATION / ATTENDANCE',
    dimension_name  VARCHAR(100) NOT NULL                COMMENT '维度名称，如「技术产出」',
    weight          DECIMAL(6,3) NOT NULL DEFAULT 1.000  COMMENT '权重，参与总分加权计算',
    max_score       DECIMAL(6,2) NOT NULL DEFAULT 100.00 COMMENT '该维度满分',
    description     VARCHAR(500) NULL                    COMMENT '维度说明，展示给打分人',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by      BIGINT       NULL                    COMMENT '创建人',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by      BIGINT       NULL                    COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perf_rule (sub_org_id, semester_id, dimension_key, deleted),
    KEY idx_perf_rule_org (sub_org_id, enabled, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '子组织绩效规则';

CREATE TABLE IF NOT EXISTS performance_weekly (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    member_id           BIGINT       NOT NULL            COMMENT '上报人人员 ID',
    member_name         VARCHAR(50)  NULL                COMMENT '姓名冗余',
    semester_id         BIGINT       NOT NULL            COMMENT '学期 ID',
    week_no             INT          NOT NULL            COMMENT '周次',
    sub_org_id          BIGINT       NOT NULL            COMMENT '所属子组织 ID',
    org_id              BIGINT       NOT NULL            COMMENT '归属组织（数据范围过滤）',
    work_content        TEXT         NOT NULL            COMMENT '本周工作内容',
    attach_file_id      BIGINT       NULL                COMMENT '附件文件 ID（可选）',
    attach_file_name    VARCHAR(300) NULL                COMMENT '附件名冗余',
    status              VARCHAR(32)  NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED 待审核 / SCORED 已打分 / REJECTED 已驳回',
    submitted_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    total_score         DECIMAL(8,2) NULL                COMMENT '加权总分',
    activity_tags       JSON         NULL                COMMENT '活跃度/功劳标签 JSON 数组',
    scored_at           DATETIME     NULL                COMMENT '打分时间',
    scored_by           BIGINT       NULL                COMMENT '打分管理员 sys_user.id',
    scored_by_name      VARCHAR(50)  NULL                COMMENT '打分人姓名冗余',
    score_comment       VARCHAR(1000) NULL               COMMENT '打分评语',
    reject_reason       VARCHAR(500) NULL                COMMENT '驳回原因',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by          BIGINT       NULL                 COMMENT '创建人',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by          BIGINT       NULL                 COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perf_weekly (member_id, semester_id, week_no, sub_org_id, deleted),
    KEY idx_perf_weekly_semester (semester_id, week_no, sub_org_id, deleted),
    KEY idx_perf_weekly_member (member_id, semester_id, deleted),
    KEY idx_perf_weekly_status (status, sub_org_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '绩效周报';

CREATE TABLE IF NOT EXISTS performance_score (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    weekly_id           BIGINT       NOT NULL            COMMENT '周报 ID',
    sub_org_id          BIGINT       NOT NULL            COMMENT '子组织 ID（冗余，便于直接聚合）',
    dimension_key       VARCHAR(64)  NOT NULL            COMMENT '维度键，对应 performance_rule',
    dimension_name      VARCHAR(100) NOT NULL            COMMENT '维度名称冗余',
    weight              DECIMAL(6,3) NOT NULL DEFAULT 1.000 COMMENT '打分时的权重快照（规则后续变更不影响历史分）',
    max_score           DECIMAL(6,2) NOT NULL DEFAULT 100.00 COMMENT '满分快照',
    score               DECIMAL(6,2) NOT NULL            COMMENT '该维度得分',
    weighted_score      DECIMAL(8,2) NULL                COMMENT '加权分 = score × weight',
    comment_text        VARCHAR(500) NULL                COMMENT '该项评语',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perf_score (weekly_id, dimension_key),
    KEY idx_perf_score_weekly (weekly_id),
    KEY idx_perf_score_org (sub_org_id, dimension_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '绩效维度得分';

CREATE TABLE IF NOT EXISTS performance_summary (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    member_id           BIGINT       NOT NULL            COMMENT '人员 ID',
    member_name         VARCHAR(50)  NULL                COMMENT '姓名冗余',
    semester_id         BIGINT       NOT NULL            COMMENT '学期 ID',
    sub_org_id          BIGINT       NOT NULL            COMMENT '子组织 ID',
    org_id              BIGINT       NOT NULL            COMMENT '归属组织',
    week_count          INT          NOT NULL DEFAULT 0  COMMENT '已打分周数',
    submitted_count     INT          NOT NULL DEFAULT 0  COMMENT '已提交周数',
    total_score         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计总分',
    avg_score           DECIMAL(8,2) NOT NULL DEFAULT 0.00 COMMENT '平均分',
    best_week_score     DECIMAL(8,2) NULL                COMMENT '最高周分',
    best_week_no        INT          NULL                COMMENT '最高分所在周次',
    recent_trend        DECIMAL(8,3) NULL                COMMENT '近4周分数趋势斜率',
    rank_in_org         INT          NULL                COMMENT '本子组织内排名',
    activity_tags       JSON         NULL                COMMENT '活跃度/功劳标签 JSON 数组',
    last_calc_at        DATETIME     NULL                COMMENT '最后聚合计算时间',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perf_summary (member_id, semester_id, sub_org_id, deleted),
    KEY idx_perf_summary_org (semester_id, sub_org_id, total_score, deleted),
    KEY idx_perf_summary_member (member_id, semester_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '绩效周期汇总';

CREATE TABLE IF NOT EXISTS ai_conversation (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    title               VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题（首条提问自动截取）',
    user_id             BIGINT       NOT NULL            COMMENT '所属用户 sys_user.id',
    member_id           BIGINT       NULL                COMMENT '所属人员 ID',
    org_id              BIGINT       NULL                COMMENT '所属组织',
    provider            VARCHAR(32)  NOT NULL DEFAULT 'ollama' COMMENT 'AI 提供方：ollama / openai',
    model               VARCHAR(100) NULL                COMMENT '模型名快照',
    message_count       INT          NOT NULL DEFAULT 0  COMMENT '消息数',
    last_message_at     DATETIME     NULL                COMMENT '最后消息时间',
    archived            TINYINT      NOT NULL DEFAULT 0  COMMENT '是否归档：1 已归档 0 正常',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_ai_conv_user (user_id, archived, deleted, last_message_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 会话';

CREATE TABLE IF NOT EXISTS ai_message (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    conversation_id     BIGINT       NOT NULL            COMMENT '会话 ID',
    role                VARCHAR(32)  NOT NULL            COMMENT '角色：user 用户 / assistant 助手 / tool 工具结果 / system 系统',
    content             LONGTEXT     NULL                COMMENT '消息内容',
    tool_name           VARCHAR(100) NULL                COMMENT '调用的工具名',
    tool_arguments      TEXT         NULL                COMMENT '工具入参 JSON',
    tool_result         LONGTEXT     NULL                COMMENT '工具返回结果 JSON（已按权限裁剪）',
    tool_call_id        VARCHAR(100) NULL                COMMENT '工具调用 ID，关联请求与结果',
    incomplete          TINYINT      NOT NULL DEFAULT 0  COMMENT '是否不完整：1 是（中断）0 否',
    prompt_tokens       INT          NULL                COMMENT '提示 token 数',
    completion_tokens   INT          NULL                COMMENT '生成 token 数',
    cost_millis         BIGINT       NULL                COMMENT '生成耗时毫秒',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_ai_msg_conv (conversation_id, deleted, created_at),
    KEY idx_ai_msg_tool (tool_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 消息';

CREATE TABLE IF NOT EXISTS semester_switch_task (
    id                  BIGINT       NOT NULL            COMMENT '主键',
    from_semester_id    BIGINT       NOT NULL            COMMENT '源学期 ID',
    from_semester_name  VARCHAR(100) NULL                COMMENT '源学期名冗余',
    to_semester_id      BIGINT       NOT NULL            COMMENT '目标学期 ID',
    to_semester_name    VARCHAR(100) NULL                COMMENT '目标学期名冗余',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING_EXECUTE' COMMENT '状态：PENDING_EXECUTE 待执行窗口内 / CANCELLED 已取消 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败',
    window_minutes      INT          NOT NULL DEFAULT 30 COMMENT '待执行窗口分钟数',
    execute_at          DATETIME     NOT NULL            COMMENT '计划的执行时间',
    started_at          DATETIME     NULL                COMMENT '实际开始执行时间',
    finished_at         DATETIME     NULL                COMMENT '执行完成时间',
    impact_snapshot     JSON         NULL                COMMENT '影响范围快照：待归档比赛数/待继承赛队数/成员关系数',
    archived_contests   INT          NOT NULL DEFAULT 0  COMMENT '归档比赛数',
    inherited_teams     INT          NOT NULL DEFAULT 0  COMMENT '继承赛队数',
    inherited_members   INT          NOT NULL DEFAULT 0  COMMENT '继承成员关系数',
    error_message       TEXT         NULL                COMMENT '失败原因',
    version             INT          NOT NULL DEFAULT 0  COMMENT '乐观锁版本号',
    initiated_by        BIGINT       NOT NULL            COMMENT '发起人 sys_user.id',
    initiated_by_name   VARCHAR(50)  NULL                COMMENT '发起人姓名冗余',
    cancelled_by        BIGINT       NULL                COMMENT '取消人',
    cancelled_at        DATETIME     NULL                COMMENT '取消时间',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_switch_status (status, execute_at),
    KEY idx_switch_semester (from_semester_id, to_semester_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学期切换任务';

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

-- ----------------------------------------------------------------------------
-- 本地消息表（2026-10-17 组长拍板新增，审批中心 Redis/MQ 升级插入点③，Flyway V2 同步追认）
-- 审批状态变更事件与 MQ 投递解耦：与状态变更同一事务落表，relay 线程异步投递；
-- 失败退避 1/2/4/8/16 分钟，超 5 次进 DEAD 留痕。无审计四列（系统事件账本无用户归属）。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notify_outbox (
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

SET FOREIGN_KEY_CHECKS = 1;