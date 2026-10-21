package com.viomanager.reply.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 论坛楼层实体，对应表 forum_reply。
 * 楼层号主题内唯一（uk_forum_reply_floor），并发撞键由服务层取号重试兜底。
 * 引用信息（楼层号/作者/摘要）在回帖时冗余冻结，免二次查库拼引用条（方案 §3.5）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("forum_reply")
public class ForumReply extends BaseEntity {

    /** 所属主题 ID */
    private Long topicId;

    /** 楼层号（主题内唯一） */
    private Integer floorNo;

    /** 回复内容（纯文本） */
    private String content;

    /** 引用楼层 ID，可空 */
    private Long quoteReplyId;

    /** 引用楼层号（冗余） */
    private Integer quoteFloorNo;

    /** 被引用人姓名快照（冗余） */
    private String quoteAuthorName;

    /** 被引用内容摘要（冗余，引用条展示用） */
    private String quoteDigest;

    /** 父评论 ID：二级评论指向一级评论（两级封顶）；一级为 NULL（V4） */
    private Long parentReplyId;

    /** 回帖人 sys_user.id */
    private Long authorUserId;

    /** 回帖人姓名快照 */
    private String authorName;

    /** 回帖人组织名快照 */
    private String authorGroup;

    /** 点赞数 */
    private Integer likeCount;

    /** 状态：NORMAL 正常 / HIDDEN 隐藏（占位显示） */
    private String status;

    /** 管理员编辑标记：0 原文 / 1 已编辑（V4，透明度口径） */
    private Integer edited;
}
