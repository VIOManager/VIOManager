package com.viomanager.kb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * KB16 状态流转请求体：{ "status": "PUBLISHED" | "OFFLINE" }。
 */
@Data
public class KbDocStatusDTO {

    /** 目标状态：PUBLISHED（上架/发布草稿）/ OFFLINE（下架） */
    @NotBlank(message = "目标状态不能为空")
    private String status;
}
