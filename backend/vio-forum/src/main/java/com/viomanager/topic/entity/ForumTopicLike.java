package com.viomanager.topic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 主题点赞实体，对应表 forum_topic_like。
 * 不继承 BaseEntity（本表只有 id + 业务列 + created_at）；点赞走硬删——
 * uk(topic_id, user_id) 与硬删配合才成立，改软删会挡住重新点赞（方案 §3.3）。
 */
@Data
@Accessors(chain = true)
@TableName("forum_topic_like")
public class ForumTopicLike {

    /** 雪花 ID（不继承 BaseEntity，须显式 ASSIGN_ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 主题 ID */
    private Long topicId;

    /** 点赞人 sys_user.id */
    private Long userId;

    /** 点赞时间（插入不设值，吃 DB 默认 CURRENT_TIMESTAMP） */
    private LocalDateTime createdAt;
}
