package com.viomanager.person.dto;

import java.util.List;

/**
 * M07 导入出参：successCount + failCount + 逐行错误（row 为 Excel 实际行号，表头是第 1 行）。
 * 口径：跳过行（已存在且有账号 / 文件内重复学号）不计成功也不计失败；
 * "已存在但无账号"的补建行与新建行同口径计成功（2026-10-24 拍板：导入自动建号）。
 */
public record PersonImportVO(int successCount, int failCount, List<RowError> errors) {

    /** 逐行错误：行号 + 原因 */
    public record RowError(int row, String reason) {
    }
}
