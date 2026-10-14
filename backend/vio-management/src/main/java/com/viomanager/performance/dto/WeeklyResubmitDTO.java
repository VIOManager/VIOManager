package com.viomanager.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PF03 驳回后重新提交入参（仅本人且 REJECTED 状态，改内容后回 SUBMITTED）。
 */
@Data
public class WeeklyResubmitDTO {

    @NotBlank(message = "workContent 不能为空")
    @Size(max = 5000, message = "workContent 最长 5000 字")
    private String workContent;

    /** 附件文件 ID（可选；null = 清空附件，传值 = 覆盖） */
    private Long attachFileId;
}
