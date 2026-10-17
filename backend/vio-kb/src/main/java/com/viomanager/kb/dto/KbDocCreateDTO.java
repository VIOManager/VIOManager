package com.viomanager.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * KB12 发布/存草稿请求体（2026-10-14 lzh 拍板简化：条目 = 外部链接或上传文件二选一，
 * 砍分类与正文——category 落库给默认值、content 列闲置，待正式需求加回）。
 * 二选一条件校验在 service：LINK 必填 linkUrl；FILE 必填 fileId + fileName。
 */
@Data
public class KbDocCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 300, message = "标题最长 300 字")
    private String title;

    @Size(max = 1000, message = "摘要最长 1000 字")
    private String summary;

    /** 条目形态：LINK 外部链接 / FILE 上传文件（拍板口径，必填） */
    @NotBlank(message = "sourceType 不能为空（LINK / FILE）")
    private String sourceType;

    /** 外部链接地址，LINK 时必填 */
    @Size(max = 1000, message = "链接最长 1000 字")
    private String linkUrl;

    /** 文件服务上传返回的 fileId（两段式：先传 /api/files），FILE 时必填 */
    private Long fileId;

    /** 原始文件名，FILE 时必填（列表展示与下载兜底） */
    @Size(max = 300, message = "文件名最长 300 字")
    private String fileName;

    /** 目标状态：PUBLISHED（缺省，直接发布）/ DRAFT（存草稿） */
    private String status;

    /** 归属组织（缺省取当前登录人 orgId，管理端归属标记） */
    private Long orgId;
}
