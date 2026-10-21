package com.viomanager.topic.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * F01 帖子列表项（不含正文）。
 */
@Data
public class TopicItemVO {

    /** 主题 ID（Jackson 字符串化） */
    private Long id;

    /** 版块编码（board_id 经 forum_board 映射） */
    private String board;

    /** 标题 */
    private String title;

    /** 作者姓名快照 */
    private String author;

    /** 作者组织名快照 */
    private String authorGroup;

    /** 标签（逗号串 split） */
    private List<String> tags;

    /** 回复数 */
    private Integer replyCount;

    /** 浏览数 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 最后回复人姓名 */
    private String lastReplyBy;

    /** 是否置顶 */
    private Boolean pinned;

    /** 状态：NORMAL / LOCKED / HIDDEN（管理员 includeHidden 列表用；常规列表恒 NORMAL/LOCKED） */
    private String status;

    /** 是否热议（= 进入 F02 热议 top5，与 F02 同一计算） */
    private Boolean hot;
}
