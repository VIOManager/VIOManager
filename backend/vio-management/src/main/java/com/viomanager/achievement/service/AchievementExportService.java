package com.viomanager.achievement.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 获奖导出服务（A07）：学生导出本人成果、管理员全量或按队伍筛选；
 * 仅审批通过的成果可导出（范围内存在待审批或已驳回 → 5008），默认 Excel（xlsx）。
 */
public interface AchievementExportService {

    /** A07 导出：teamId 可选筛选（学生只能导本人的）；范围内无可导出成果 1004 */
    void export(Long teamId, HttpServletResponse response);
}
