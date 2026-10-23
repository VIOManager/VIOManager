package com.viomanager.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PF02 提交周报入参。subOrgId 服务端取本人所属组织（member.org_id），不由前端传。
 */
@Data
public class WeeklySubmitDTO {

    @NotNull(message = "weekNo 不能为空")
    @jakarta.validation.constraints.Min(value = 1, message = "weekNo 从 1 起")
    private Integer weekNo;

    @NotBlank(message = "workContent 不能为空")
    @Size(max = 5000, message = "workContent 最长 5000 字")
    private String workContent;

    /** 附件文件 ID（可选，走骨架 F01 先拿 fileId） */
    private Long attachFileId;
}
