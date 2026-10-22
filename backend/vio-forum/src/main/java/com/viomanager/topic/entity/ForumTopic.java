package com.viomanager.topic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 论坛主题实体，对应表 forum_topic。
 * 状态机：NORMAL 正常 / LOCKED 锁定禁回 / HIDDEN 隐藏（恢复一律回 NORMAL）。
 * 姓名与组织为写入时冻结的快照（跨库零 JOIN 的根基，方案 §3.5）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("forum_topic")
public class ForumTopic extends BaseEntity {

    /** 版块 ID */
    private Long boardId;

    /** 标题 */
    private String title;

    /** 正文（纯文本，\n 分段，DTO 出参按段 split） */
    private String body;

    /** 标签，逗号分隔 */
    private String tags;

    /** 发帖人 sys_user.id（JWT 载荷，跨库逻辑引用） */
    private Long authorUserId;

    /** 发帖人姓名快照（发帖时从 JWT realName 冻结） */
    private String authorName;

    /** 发帖人组织名快照（发帖时经 forum_user 冻结） */
    private String authorGroup;

    /** 状态：NORMAL / LOCKED / HIDDEN */
    private String status;

    /** 置顶：1 是 0 否（标志位，DTO 出入参布尔化） */
    private Integer pinned;

    /** 回复数（回帖事务内维护） */
    private Integer replyCount;

    /** 浏览数（详情接口 +1） */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer collectCount;

    /** 最后活跃时间（排序键；发帖时 = 创建时间，回帖时刷新） */
    private LocalDateTime lastReplyAt;

    /** 最后回复人 */
    private Long lastReplyUserId;

    /** 最后回复人姓名快照 */
    private String lastReplyByName;
}
