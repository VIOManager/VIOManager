package com.viomanager.person.dto;

import java.util.List;

/**
 * M07 导入出参：successCount + failCount + 逐行错误（row 为 Excel 实际行号，表头是第 1 行）。
 * 注意：契约约定"学号已存在的行跳过"——跳过的行既不计成功也不计失败（可重复导入幂等）。
 */
public record PersonImportVO(int successCount, int failCount, List<RowError> errors) {

    /** 逐行错误：行号 + 原因 */
    public record RowError(int row, String reason) {
    }
}
