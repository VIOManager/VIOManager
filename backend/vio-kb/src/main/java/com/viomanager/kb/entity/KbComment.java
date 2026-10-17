package com.viomanager.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库评论（kb_comment）。
 * 刻意不继承 BaseEntity：本表没有 created_by/updated_by 列（审计列不全，BaseEntity 注释同款约定），
 * 评论人身份在 member_id / member_name（发表时快照，不回溯）。
 * member_id 口径：JWT 的 userId（sys_user.id）——独立服务无 member 表，见方案文档待确认项 1。
 */
@Data
@TableName("kb_comment")
public class KbComment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 雪花 ID（JSON 出参按字符串序列化） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属文档 ID */
    private Long docId;

    /** 父评论 ID（顶层评论为 null；回复非空） */
    private Long parentId;

    /** 评论内容 */
    private String content;

    /** 评论人 ID（JWT userId，见类注释） */
    private Long memberId;

    /** 评论人姓名冗余（token realName 快照，改名不回溯） */
    private String memberName;

    /** 状态：VISIBLE / HIDDEN（见 KbCommentStatus） */
    private String status;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
