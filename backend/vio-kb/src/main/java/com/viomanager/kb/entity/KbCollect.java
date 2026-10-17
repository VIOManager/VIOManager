package com.viomanager.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库收藏（kb_collect）。
 * 刻意不继承 BaseEntity：本表仅 created_at 一列审计字段（BaseEntity 注释同款约定）。
 * member_id 口径：JWT 的 userId（独立服务无 member 表，见方案文档待确认项 1）。
 * 防重复收藏靠唯一键 uk_kb_collect(doc_id, member_id)，撞键转 9001。
 */
@Data
@TableName("kb_collect")
public class KbCollect implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 文档 ID */
    private Long docId;

    /** 收藏人 ID（JWT userId，见类注释） */
    private Long memberId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
