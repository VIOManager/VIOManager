package com.viomanager.topic.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * F04 帖子详情（主楼）。
 */
@Data
public class TopicDetailVO {

    /** 主题 ID */
    private Long id;

    /** 版块编码 */
    private String board;

    /** 标题 */
    private String title;

    /** 作者姓名快照 */
    private String author;

    /** 作者组织名快照 */
    private String authorGroup;

    /** 标签 */
    private List<String> tags;

    /** 回复数 */
    private Integer replyCount;

    /** 浏览数 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 当前用户是否已赞 */
    private Boolean liked;

    /** 当前用户是否已收藏 */
    private Boolean collected;

    /** 是否置顶 */
    private Boolean pinned;

    /** 状态：NORMAL / LOCKED / HIDDEN（V4 起透出，管理员前端出锁定/恢复按钮；解决观察项 S-F1） */
    private String status;

    /** 发布时间（= 创建时间） */
    private LocalDateTime publishAt;

    /** 正文按段（库内 \n 分段 split） */
    private List<String> body;

    /** 附件（本期恒空数组——契约降级，方案 §3.5） */
    private List<Object> attach;

    /** 作者统计 */
    private AuthorStatsVO authorStats;
}
