package com.viomanager.topic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * F06 发帖入参（幂等：一张表单一个 X-Idempotency-Key）。
 */
@Data
public class TopicCreateDTO {

    /** 版块编码（GENERAL/EE/MODELING/AI） */
    @NotBlank(message = "版块不能为空")
    private String board;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不超过 200 字")
    private String title;

    /** 正文纯文本（空行分段，详情页按段渲染） */
    @NotBlank(message = "正文不能为空")
    private String bodyText;

    /** 标签，可空（服务端 join 成逗号串存库） */
    private List<String> tags;

    /** 置顶（仅超管生效，其他角色静默置 false——前端勾选框本就只对管理员可见） */
    private Boolean pinned;
}
