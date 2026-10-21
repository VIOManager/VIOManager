package com.viomanager.reply.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * F05 楼层项（引用信息由服务端拼好，方案 §3.5；两级评论 V4 扩展）。
 */
@Data
public class ReplyItemVO {

    /** 楼层 ID */
    private Long id;

    /** 楼层号（主题内从 1 起；二级评论也取号保唯一键，但前端不展示二级楼层号） */
    private Integer floorNo;

    /** 作者姓名快照 */
    private String author;

    /** 作者组织名快照 */
    private String authorGroup;

    /** 发布时间 */
    private LocalDateTime publishAt;

    /** 回复内容（HIDDEN 楼层对学生占位为"该楼层已被隐藏"，管理员可见原文） */
    private String text;

    /** 点赞数 */
    private Integer likeCount;

    /** 当前用户是否已赞 */
    private Boolean liked;

    /** 引用条（无引用为 null） */
    private QuoteVO quote;

    /** 父评论 ID：一级为 null，二级指向一级（V4） */
    private Long parentReplyId;

    /** 二级评论（仅一级楼层携带，时间正序全量；二级自身恒为空列表） */
    private List<ReplyItemVO> children;

    /** 状态：NORMAL / HIDDEN（管理员前端据此出"恢复"按钮） */
    private String status;

    /** 管理员编辑标记：0 原文 / 1 已编辑（透明度） */
    private Boolean edited;

    /**
     * 引用条载荷。
     */
    @Data
    public static class QuoteVO {

        /** 被引用人姓名 */
        private String author;

        /** 被引用楼层号 */
        private Integer floorNo;

        /** 被引用内容摘要 */
        private String text;
    }
}
