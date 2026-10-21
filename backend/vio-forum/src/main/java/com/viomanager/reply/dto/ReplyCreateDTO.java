package com.viomanager.reply.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * F07 回帖入参（幂等：一张表单一个 X-Idempotency-Key）。
 */
@Data
public class ReplyCreateDTO {

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 5000, message = "回复内容不超过 5000 字")
    private String text;

    /** 引用楼层 ID，可空 */
    private Long quoteReplyId;

    /**
     * 父评论 ID，可空：传值即为二级评论（对一级评论的回复，两级封顶，V4）。
     * 父校验：同主题 + 必须一级 + 未隐藏未删，否则 9105；对二级再回 → 9106。
     */
    private Long parentReplyId;
}
