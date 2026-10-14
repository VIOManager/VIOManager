package com.viomanager.kb.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库详情（KB02）：列表字段 + 正文 + 附件信息 + 当前用户是否已收藏。
 * 附件"信息"= fileId 弱引用与来源链接；文件名等元数据属主服务 file_object，
 * 联调时如需展示名称由前端另行处理或后续扩展（方案待确认项 4）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KbDocDetailVO extends KbDocVO {

    /** 正文 Markdown */
    private String content;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 附件文件 ID */
    private Long attachFileId;

    /** 归属组织（管理端标记） */
    private Long orgId;

    /** AI 总结模型名 */
    private String aiModel;

    /** 当前登录用户是否已收藏（强一致个性化字段，永不缓存） */
    private Boolean isCollected;
}
