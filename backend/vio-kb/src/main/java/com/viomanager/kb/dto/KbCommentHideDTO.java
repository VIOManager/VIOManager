package com.viomanager.kb.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * KB11 隐藏/恢复请求体：{ "hidden": true 隐藏 | false 恢复 }。
 */
@Data
public class KbCommentHideDTO {

    /** true=隐藏（VISIBLE→HIDDEN），false=恢复（HIDDEN→VISIBLE） */
    @NotNull(message = "hidden 不能为空")
    private Boolean hidden;
}
