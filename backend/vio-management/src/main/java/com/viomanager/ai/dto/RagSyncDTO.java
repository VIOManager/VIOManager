package com.viomanager.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * KB → AI 的向量同步请求体（拆步 2 薄通知）。
 * 契约：content 非空 = 全量重切该文档（先删旧块再入库，幂等不残留）；
 * content 空 = 该文档已下架/删除，只清索引。
 */
@Data
public class RagSyncDTO {

    /** KB 文档 id（跨服务逻辑关联；metadata 里存字符串形式，见 RagIndexService） */
    @NotNull(message = "docId 不能为空")
    private Long docId;

    /** 文档标题（回答带出处用，冗余进 metadata 免跨库回查） */
    @Size(max = 300, message = "文档标题最长 300 字")
    private String docTitle;

    /** 可检索正文（MANUAL=markdown 正文；LINK/FILE=标题+摘要）；空=清索引 */
    private String content;
}
