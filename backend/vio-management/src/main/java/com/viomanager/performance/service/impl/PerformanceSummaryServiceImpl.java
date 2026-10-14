package com.viomanager.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.performance.entity.PerformanceSummary;
import com.viomanager.performance.mapper.PerformanceSummaryMapper;
import com.viomanager.performance.service.PerformanceSummaryService;
import com.viomanager.performance.support.PerfCalcSupport;
import com.viomanager.schedule.support.ScheduleMemberSupport;
import com.viomanager.schedule.support.SemesterSupport;
import com.viomanager.schedule.support.SubOrgSupport;
import com.viomanager.performance.vo.MyPerfSummaryVO;
import com.viomanager.performance.vo.SummaryRowVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * 汇总服务实现。PF05 免参取当前激活学期；未提交过周报的成员返回全零占位（前端展示用）。
 */
@Service
public class PerformanceSummaryServiceImpl implements PerformanceSummaryService {

    @Resource
    private PerformanceSummaryMapper summaryMapper;

    @Resource
    private ScheduleMemberSupport memberSupport;

    @Resource
    private SemesterSupport semesterSupport;

    @Resource
    private SubOrgSupport subOrgSupport;

    @Override
    public MyPerfSummaryVO mySummary() {
        Long memberId = memberSupport.requireCurrentMemberId();
        SemesterSupport.SemesterRow semester = requireCurrentSemester();
        ScheduleMemberSupport.MemberBasic member = memberSupport.requireMember(memberId);
        MyPerfSummaryVO vo = new MyPerfSummaryVO();
        vo.setSemesterId(semester.id());
        vo.setSemesterName(semester.name());
        vo.setTotalScore(BigDecimal.ZERO);
        vo.setAvgScore(BigDecimal.ZERO);
        vo.setScoredCount(0);
        vo.setSubmitCount(0);
        vo.setBestWeekNo(0);
        vo.setBestWeekScore(BigDecimal.ZERO);
        vo.setRankInOrg(0);
        vo.setTrendSlope(BigDecimal.ZERO);
        vo.setTags(List.of());
        vo.setOrgMemberCount(0);
        if (member.orgId() == null) {
            return vo;
        }
        subOrgSupport.requireSubOrg(member.orgId());
        List<PerformanceSummary> orgRows = summaryMapper.selectList(new LambdaQueryWrapper<PerformanceSummary>()
                .eq(PerformanceSummary::getSemesterId, semester.id())
                .eq(PerformanceSummary::getSubOrgId, member.orgId()));
        vo.setOrgMemberCount(orgRows.size());
        PerformanceSummary mine = orgRows.stream()
                .filter(row -> row.getMemberId().equals(memberId))
                .findFirst().orElse(null);
        if (mine == null) {
            return vo;
        }
        vo.setTotalScore(nvl(mine.getTotalScore()));
        vo.setAvgScore(nvl(mine.getAvgScore()));
        vo.setScoredCount(mine.getWeekCount() == null ? 0 : mine.getWeekCount());
        vo.setSubmitCount(mine.getSubmittedCount() == null ? 0 : mine.getSubmittedCount());
        vo.setBestWeekNo(mine.getBestWeekNo() == null ? 0 : mine.getBestWeekNo());
        vo.setBestWeekScore(nvl(mine.getBestWeekScore()));
        vo.setRankInOrg(mine.getRankInOrg() == null ? 0 : mine.getRankInOrg());
        vo.setTrendSlope(nvl(mine.getRecentTrend()));
        vo.setTags(PerfCalcSupport.parseTags(mine.getActivityTags()));
        return vo;
    }

    @Override
    public List<SummaryRowVO> summaries(Long semesterId, Long subOrgId, String memberKeyword) {
        List<Long> visible = subOrgSupport.visibleOrgIds(UserHolder.getUser());
        Long effectiveSemester = semesterId != null ? semesterId : requireCurrentSemester().id();
        List<PerformanceSummary> rows = summaryMapper.selectList(new LambdaQueryWrapper<PerformanceSummary>()
                .eq(PerformanceSummary::getSemesterId, effectiveSemester)
                .eq(subOrgId != null, PerformanceSummary::getSubOrgId, subOrgId)
                .like(memberKeyword != null && !memberKeyword.isBlank(),
                        PerformanceSummary::getMemberName, memberKeyword)
                .in(visible != null, PerformanceSummary::getOrgId, visible)
                .orderByDesc(PerformanceSummary::getTotalScore));
        Map<Long, String> orgNames = subOrgSupport.orgNames(
                rows.stream().map(PerformanceSummary::getSubOrgId).distinct().toList());
        return rows.stream().map(row -> {
            SummaryRowVO vo = new SummaryRowVO();
            vo.setMemberId(row.getMemberId());
            vo.setMemberName(row.getMemberName());
            vo.setSubOrgName(orgNames.getOrDefault(row.getSubOrgId(), "未知组织"));
            vo.setSubmitCount(row.getSubmittedCount());
            vo.setScoredCount(row.getWeekCount());
            vo.setTotalScore(nvl(row.getTotalScore()));
            vo.setAvgScore(nvl(row.getAvgScore()));
            vo.setBestWeekNo(row.getBestWeekNo());
            vo.setRankInOrg(row.getRankInOrg());
            vo.setTags(PerfCalcSupport.parseTags(row.getActivityTags()));
            return vo;
        }).toList();
    }

    @Override
    public void export(Long semesterId, Long subOrgId, String groupBy, HttpServletResponse response) {
        boolean byOrg = "ORG".equalsIgnoreCase(groupBy);
        List<SummaryRowVO> rows = summaries(semesterId, subOrgId, null);
        String fileName = byOrg ? "绩效汇总-按子组织.xlsx" : "绩效汇总-按人.xlsx";
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("绩效汇总");
            if (byOrg) {
                appendHeader(sheet, "子组织", "人数", "累计总分", "平均分");
                Map<String, List<SummaryRowVO>> byOrgName = rows.stream()
                        .collect(Collectors.groupingBy(SummaryRowVO::getSubOrgName,
                                LinkedHashMap::new, Collectors.toList()));
                byOrgName.forEach((orgName, group) -> {
                    Row r = sheet.createRow(sheet.getLastRowNum() + 1);
                    r.createCell(0).setCellValue(orgName);
                    r.createCell(1).setCellValue(group.size());
                    r.createCell(2).setCellValue(group.stream().map(SummaryRowVO::getTotalScore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue());
                    r.createCell(3).setCellValue(group.stream().map(SummaryRowVO::getAvgScore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(group.size()), 2, RoundingMode.HALF_UP).doubleValue());
                });
            } else {
                appendHeader(sheet, "姓名", "子组织", "提交周数", "打分周数", "总分", "平均分", "最高分周", "排名", "标签");
                rows.forEach(row -> {
                    Row r = sheet.createRow(sheet.getLastRowNum() + 1);
                    r.createCell(0).setCellValue(row.getMemberName());
                    r.createCell(1).setCellValue(row.getSubOrgName());
                    r.createCell(2).setCellValue(row.getSubmitCount());
                    r.createCell(3).setCellValue(row.getScoredCount());
                    r.createCell(4).setCellValue(row.getTotalScore().doubleValue());
                    r.createCell(5).setCellValue(row.getAvgScore().doubleValue());
                    r.createCell(6).setCellValue(row.getBestWeekNo() == null ? 0 : row.getBestWeekNo());
                    r.createCell(7).setCellValue(row.getRankInOrg() == null ? 0 : row.getRankInOrg());
                    r.createCell(8).setCellValue(String.join("、", row.getTags()));
                });
            }
            wb.write(out);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\""
                    + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8) + "\"");
            response.getOutputStream().write(out.toByteArray());
            response.getOutputStream().flush();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    private void appendHeader(Sheet sheet, String... headers) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            sheet.setColumnWidth(i, 16 * 256);
        }
    }

    private SemesterSupport.SemesterRow requireCurrentSemester() {
        SemesterSupport.SemesterRow current = semesterSupport.currentActive();
        if (current == null) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "当前无激活学期");
        }
        return current;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
