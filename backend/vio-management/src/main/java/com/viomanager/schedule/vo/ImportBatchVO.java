package com.viomanager.schedule.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 课表导入批次出参（TT03 列表项 / TT04 详情，前端 ImportBatch 消费形状）。
 * semesterName 联 semester 表带出；PENDING 未归一化时为 null。
 */
@Data
public class ImportBatchVO {

    /** 批次 ID */
    private Long id;

    /** 学期 ID（未归一化为 null） */
    private Long semesterId;

    /** 学期名称 */
    private String semesterName;

    /** 状态：PENDING / MAPPING / COMPLETED / FAILED */
    private String status;

    /** 总行数 */
    private Integer totalCount;

    /** 成功行数 */
    private Integer successCount;

    /** 失败行数 */
    private Integer failCount;

    /** 导入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** TT04：每行错误明细（行号 + 原因），无错误为空数组 */
    private List<ErrorLine> errors;

    /** 行级错误。 */
    @lombok.Data
    public static class ErrorLine {

        /** Excel 行号（1 起，含表头偏移） */
        private int rowNo;

        /** 失败原因 */
        private String reason;

        public ErrorLine() {
        }

        public ErrorLine(int rowNo, String reason) {
            this.rowNo = rowNo;
            this.reason = reason;
        }
    }
}
