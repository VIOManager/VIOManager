package com.viomanager.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * KB12 发布/存草稿请求体。
 * 条件校验在 service：LINK 必填 sourceUrl；isPrompt=1 必填 promptSchema（清单 §5）。
 */
@Data
public class KbDocCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 300, message = "标题最长 300 字")
    private String title;

    @NotBlank(message = "分类不能为空")
    @Size(max = 64, message = "分类最长 64 字")
    private String category;

    /** 是否 AI 提示词：1 是 0 否（缺省 0） */
    private Integer isPrompt;

    /** 提示词结构模板（JSON 字符串），isPrompt=1 时必填 */
    private String promptSchema;

    /** 正文 Markdown（MANUAL/AI_SUMMARY 类型） */
    private String content;

    @Size(max = 1000, message = "摘要最长 1000 字")
    private String summary;

    /** 封面文件 ID（先走 /api/files 拿 fileId 再提交） */
    private Long coverFileId;

    /** 附件文件 ID（单附件，表结构即契约） */
    private Long attachFileId;

    /** 来源：MANUAL（缺省）/ LINK / AI_SUMMARY */
    private String sourceType;

    /** 外部来源链接，LINK 必填 */
    private String sourceUrl;

    @Size(max = 300, message = "标签最长 300 字")
    private String tags;

    /** 目标状态：PUBLISHED（缺省，直接发布）/ DRAFT（存草稿） */
    private String status;

    /** 归属组织（缺省取当前登录人 orgId，管理端归属标记） */
    private Long orgId;

    /** AI 总结时使用的模型名（AI_SUMMARY 类型用） */
    private String aiModel;
}
