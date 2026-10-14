package com.viomanager.kb.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * KB13 编辑请求体（全字段可选，只改传入的字段）。
 * 携带 updatedAt（读取时的更新时间）即启用乐观锁：条件更新 0 行命中返回 1007（规范预留码），
 * 不携带则直改——前端联调时决定是否带。
 */
@Data
public class KbDocUpdateDTO {

    @Size(max = 300, message = "标题最长 300 字")
    private String title;

    @Size(max = 64, message = "分类最长 64 字")
    private String category;

    private Integer isPrompt;

    private String promptSchema;

    private String content;

    @Size(max = 1000, message = "摘要最长 1000 字")
    private String summary;

    private Long coverFileId;

    private Long attachFileId;

    private String sourceType;

    private String sourceUrl;

    private String tags;

    private String aiModel;

    private Long orgId;

    /** 乐观锁锚点：编辑页打开时读到的 updated_at（yyyy-MM-dd HH:mm:ss），可选 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
