package com.viomanager.topic.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * F12 主题状态设置入参：to ∈ {NORMAL, LOCKED, HIDDEN}。
 * 删除=HIDDEN（软删可恢复）、恢复=NORMAL、锁定/解锁=LOCKED/NORMAL（2026-10-17 拍板 P2）。
 */
@Data
public class TopicStatusDTO {

    @NotBlank(message = "to 不能为空")
    private String to;
}
