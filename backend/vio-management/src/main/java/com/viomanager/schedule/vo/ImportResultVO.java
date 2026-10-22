package com.viomanager.schedule.vo;

import lombok.Data;

/**
 * TT02 上传导入返回（前端 schedule.ts apiTimetableImport 消费形状）：批次 ID 与成功失败数。
 */
@Data
public class ImportResultVO {

    /** 批次 ID */
    private Long batchId;

    /** 总行数（有效解析行 + 错误行） */
    private Integer totalCount;

    /** 成功行数 */
    private Integer successCount;

    /** 失败行数 */
    private Integer failCount;
}
