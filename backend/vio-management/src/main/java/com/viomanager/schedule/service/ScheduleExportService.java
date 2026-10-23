package com.viomanager.schedule.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 排班导出服务（SC08/SC09，接口清单 §6，仅管理员）。
 */
public interface ScheduleExportService {

    /** SC08 周排班表导出：semesterId + weekNo + subOrgId，xlsx 流。 */
    void exportWeek(Long semesterId, Integer weekNo, Long subOrgId, HttpServletResponse response);

    /** SC09 学期排班统计导出：每人排班次数与明细汇总，xlsx 流。 */
    void exportStats(Long semesterId, Long subOrgId, HttpServletResponse response);
}
