package com.viomanager.topic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 主题收藏实体，对应表 forum_topic_collect。同 ForumTopicLike：不继承 BaseEntity、硬删。
 */
@Data
@Accessors(chain = true)
@TableName("forum_topic_collect")
public class ForumTopicCollect {

    /** 雪花 ID（显式 ASSIGN_ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 主题 ID */
    private Long topicId;

    /** 收藏人 sys_user.id */
    private Long userId;

    /** 收藏时间（吃 DB 默认） */
    private LocalDateTime createdAt;
}
