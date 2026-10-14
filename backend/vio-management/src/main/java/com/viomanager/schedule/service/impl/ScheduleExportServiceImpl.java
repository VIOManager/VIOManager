package com.viomanager.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.common.enums.ScheduleErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.schedule.entity.Schedule;
import com.viomanager.schedule.mapper.ScheduleMapper;
import com.viomanager.schedule.service.ScheduleExportService;
import com.viomanager.schedule.support.SemesterSupport;
import com.viomanager.schedule.support.SubOrgSupport;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * 排班导出实现（POI xlsx 流）。可见性与查询接口同口径：部门管理员仅本子树
 * （复用 ScheduleServiceImpl 同款校验，见 visibleOrgIds 调用）。
 */
@Service
public class ScheduleExportServiceImpl implements ScheduleExportService {

    private static final String[] WEEK_HEADERS = {"星期", "节次", "姓名", "班级", "值班内容", "课表冲突", "冲突说明", "备注"};
    private static final String[] STATS_HEADERS = {"姓名", "班级", "排班次数", "明细（周次:星期节次）"};

    @Resource
    private ScheduleMapper scheduleMapper;

    @Resource
    private SemesterSupport semesterSupport;

    @Resource
    private SubOrgSupport subOrgSupport;

    @Override
    public void exportWeek(Long semesterId, Integer weekNo, Long subOrgId, HttpServletResponse response) {
        semesterSupport.checkWeekInRange(semesterId, weekNo, ScheduleErrorCode.WEEK_OUT_OF_RANGE);
        subOrgSupport.requireSubOrg(subOrgId);
        checkVisible(subOrgId);
        List<Schedule> rows = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId)
                .eq(Schedule::getWeekNo, weekNo)
                .eq(Schedule::getSubOrgId, subOrgId)
                .eq(Schedule::getStatus, "PLANNED")
                .orderByAsc(Schedule::getWeekday)
                .orderByAsc(Schedule::getSlotIndex));
        String orgName = subOrgSupport.requireOrg(subOrgId).name();
        writeXlsx(response, "周排班表-" + orgName + "-第" + weekNo + "周.xlsx", wb -> {
            Sheet sheet = wb.createSheet("第" + weekNo + "周");
            appendHeader(sheet, WEEK_HEADERS);
            rows.forEach(row -> {
                Row r = sheet.createRow(sheet.getLastRowNum() + 1);
                r.createCell(0).setCellValue("周" + "一二三四五六日".charAt(row.getWeekday() - 1));
                r.createCell(1).setCellValue(row.getSlotIndex());
                r.createCell(2).setCellValue(nvl(row.getMemberName()));
                r.createCell(3).setCellValue(nvl(row.getMemberClassName()));
                r.createCell(4).setCellValue(nvl(row.getDutyName()));
                r.createCell(5).setCellValue(row.getCourseConflict() != null && row.getCourseConflict() == 1 ? "是" : "否");
                r.createCell(6).setCellValue(nvl(row.getConflictNote()));
                r.createCell(7).setCellValue(nvl(row.getRemark()));
            });
        });
    }

    @Override
    public void exportStats(Long semesterId, Long subOrgId, HttpServletResponse response) {
        semesterSupport.requireSemester(semesterId);
        if (subOrgId != null) {
            subOrgSupport.requireSubOrg(subOrgId);
            checkVisible(subOrgId);
        }
        List<Schedule> rows = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId)
                .eq(subOrgId != null, Schedule::getSubOrgId, subOrgId)
                .eq(Schedule::getStatus, "PLANNED")
                .orderByAsc(Schedule::getMemberId)
                .orderByAsc(Schedule::getWeekNo));
        String orgName = subOrgId == null ? "全部子组织" : subOrgSupport.requireOrg(subOrgId).name();
        writeXlsx(response, "学期排班统计-" + orgName + ".xlsx", wb -> {
            Sheet sheet = wb.createSheet("排班统计");
            appendHeader(sheet, STATS_HEADERS);
            Map<Long, List<Schedule>> byMember = rows.stream()
                    .collect(Collectors.groupingBy(Schedule::getMemberId, LinkedHashMap::new, Collectors.toList()));
            byMember.values().forEach(memberRows -> {
                Schedule first = memberRows.get(0);
                Row r = sheet.createRow(sheet.getLastRowNum() + 1);
                r.createCell(0).setCellValue(nvl(first.getMemberName()));
                r.createCell(1).setCellValue(nvl(first.getMemberClassName()));
                r.createCell(2).setCellValue(memberRows.size());
                r.createCell(3).setCellValue(memberRows.stream()
                        .map(s -> "第" + s.getWeekNo() + "周:周" + "一二三四五六日".charAt(s.getWeekday() - 1)
                                + "第" + s.getSlotIndex() + "节")
                        .collect(Collectors.joining("，")));
            });
        });
    }

    /** xlsx 生成模板：调用方只管填 sheet，字节输出与响应头统一处理。 */
    private interface SheetFiller {
        void fill(org.apache.poi.xssf.usermodel.XSSFWorkbook wb);
    }

    private void writeXlsx(HttpServletResponse response, String fileName, SheetFiller filler) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filler.fill(wb);
            wb.write(out);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\""
                    + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8) + "\"");
            response.getOutputStream().write(out.toByteArray());
            response.getOutputStream().flush();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(com.viomanager.common.enums.CommonErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    private void appendHeader(Sheet sheet, String[] headers) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            sheet.setColumnWidth(i, 16 * 256);
        }
    }

    private void checkVisible(Long subOrgId) {
        List<Long> visible = subOrgSupport.visibleOrgIds(
                com.viomanager.common.auth.UserHolder.getUser());
        if (visible != null && !visible.contains(subOrgId)) {
            throw new BizException(com.viomanager.common.enums.CommonErrorCode.FORBIDDEN, "无权导出该子组织的排班");
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
