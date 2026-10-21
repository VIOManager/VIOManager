package com.viomanager.reply.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 楼层点赞实体，对应表 forum_reply_like。不继承 BaseEntity、硬删（同主题点赞表口径）。
 */
@Data
@Accessors(chain = true)
@TableName("forum_reply_like")
public class ForumReplyLike {

    /** 雪花 ID（显式 ASSIGN_ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 楼层 ID */
    private Long replyId;

    /** 点赞人 sys_user.id */
    private Long userId;

    /** 点赞时间（吃 DB 默认） */
    private LocalDateTime createdAt;
}
