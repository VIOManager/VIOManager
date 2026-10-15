package com.viomanager.kb.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * KB13 编辑请求体（全字段可选，只改传入的字段）。
 * 形态（sourceType）创建后不可改——切换形态=删除重建（拍板口径：形态切换只在发布表单）。
 * 携带 updatedAt（读取时的更新时间）即启用乐观锁：条件更新 0 行命中返回 1007（规范预留码），
 * 不携带则直改——前端联调时决定是否带。
 */
@Data
public class KbDocUpdateDTO {

    @Size(max = 300, message = "标题最长 300 字")
    private String title;

    @Size(max = 1000, message = "摘要最长 1000 字")
    private String summary;

    /** 外部链接（LINK 条目改链接用；LINK 条目不允许清空，校验在 service） */
    @Size(max = 1000, message = "链接最长 1000 字")
    private String linkUrl;

    /** 替换附件（FILE 条目换文件用，两段式拿新 fileId） */
    private Long fileId;

    /** 替换后的原始文件名 */
    @Size(max = 300, message = "文件名最长 300 字")
    private String fileName;

    /** 乐观锁锚点：编辑页打开时读到的 updated_at（yyyy-MM-dd HH:mm:ss），可选 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
