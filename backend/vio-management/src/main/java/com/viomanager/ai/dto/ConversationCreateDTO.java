package com.viomanager.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI03 新建会话入参。body 可整体省略；title 不传走默认"新对话"，
 * 首条提问后由聊天主干自动截前 20 字覆盖（接口清单 §4）。
 */
@Data
public class ConversationCreateDTO {

    /** 可选标题；列宽 200，留余量 */
    @Size(max = 200, message = "标题最长 200 字")
    private String title;
}
