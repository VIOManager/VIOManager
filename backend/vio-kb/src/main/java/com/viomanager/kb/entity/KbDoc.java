package com.viomanager.kb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档（kb_doc）。列定义与 docs/db/initsql.sql 一致（表结构即契约）。
 * 计数三列（view/collect/comment_count）只允许原子自增/自减，禁止读改写；
 * status 流转只允许条件 UPDATE（见 KbDocMapper#casStatus），防并发跳档。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_doc")
public class KbDoc extends BaseEntity {

    /** 标题（FULLTEXT 检索列之一） */
    private String title;

    /** 分类，对应字典 KB_CATEGORY */
    private String category;

    /** 是否 AI 提示词：1 是 0 否 */
    private Integer isPrompt;

    /** 提示词结构模板（JSON 字符串），仅 is_prompt=1 时有值 */
    private String promptSchema;

    /** 正文内容（Markdown，LONGTEXT）——⚠️ 列表查询禁止 SELECT 本列 */
    private String content;

    /** 摘要（FULLTEXT 检索列之一，列表卡片用） */
    private String summary;

    /** 封面文件 ID（弱引用主服务 file_object，无外键） */
    private Long coverFileId;

    /** 附件文件 ID（弱引用主服务 file_object，无外键；FILE 形态必填） */
    private Long attachFileId;

    /** 附件原始文件名（FILE 形态冗余，V2：免跨服务查文件元数据） */
    private String fileName;

    /** 来源：LINK / FILE（新建口径，见 KbSourceType；MANUAL/AI_SUMMARY 为历史与扩展值） */
    private String sourceType;

    /** 外部来源链接，LINK 类型必填 */
    private String sourceUrl;

    /** 标签，逗号分隔 */
    private String tags;

    /** 状态：DRAFT / PUBLISHED / OFFLINE（见 KbDocStatus） */
    private String status;

    /** 是否置顶：1 是 0 否 */
    private Integer topFlag;

    /** 浏览数（详情页同步原子 +1） */
    private Integer viewCount;

    /** 收藏数（与 kb_collect 行数一致，增删同事务 ±1） */
    private Integer collectCount;

    /** 评论数（口径=可见数，发/删/隐藏/恢复随口径 ±1） */
    private Integer commentCount;

    /** AI 总结时使用的模型名 */
    private String aiModel;

    /** 归属组织（管理端归属标记；浏览侧不按此过滤——PUBLISHED 全员可见） */
    private Long orgId;

    /** 发布人姓名（JWT realName 快照，V2：独立库无用户表，冗余免跨服务查询） */
    private String publisherName;
}
