package com.viomanager.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * KB09 发表/回复评论请求体。
 */
@Data
public class KbCommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论最长 2000 字")
    private String content;

    /** 父评论 ID（顶层评论为空；回复非空，service 校验父评论存在且同文档） */
    private Long parentId;
}
