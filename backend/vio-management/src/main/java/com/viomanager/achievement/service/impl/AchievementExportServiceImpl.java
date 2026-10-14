package com.viomanager.achievement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.achievement.entity.Achievement;
import com.viomanager.achievement.mapper.AchievementMapper;
import com.viomanager.achievement.service.AchievementExportService;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MaterialErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MemberIdSupport;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * 获奖导出实现（A07）：
 * 范围——学生固定限定本人（owner_member_id = 当前人档案 ID，未绑档案导不出东西），
 * 管理员全量或按 teamId 筛；5008 闸门——范围内存在任何未通过（待审批/已驳回）成果即拒；
 * 输出 xlsx（POI），列：成果名称 / 类型 / 等级 / 获奖日期 / 归属人 / 队伍。
 */
@Slf4j
@Service
public class AchievementExportServiceImpl implements AchievementExportService {

    @Resource
    private AchievementMapper achievementMapper;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Override
    public void export(Long teamId, HttpServletResponse response) {
        //1.范围：学生只导本人（按归属人，与队长/队员身份无关）；管理员全量或按队伍筛
        CurrentUser user = UserHolder.getUser();
        boolean admin = user != null && ("SUPER_ADMIN".equals(user.getRole())
                || "DEPT_ADMIN".equals(user.getRole()));
        QueryWrapper<Achievement> wrapper = new QueryWrapper<>();
        if (!admin) {
            Long memberId = memberIdSupport.currentMemberId(user);
            wrapper.eq("owner_member_id", memberId == null ? -1L : memberId);
        }
        if (teamId != null) {
            wrapper.eq("team_id", teamId);
        }
        List<Achievement> achievements = achievementMapper.selectList(wrapper);
        //2.5008 闸门：范围内存在待审批或已驳回成果即拒（错误码表口径，宁拒不漏）
        for (Achievement achievement : achievements) {
            if (!"APPROVED".equals(achievement.getStatus())) {
                throw new BizException(MaterialErrorCode.EXPORT_REQUIRES_APPROVED);
            }
        }
        if (achievements.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "没有可导出的成果");
        }
        //3.队伍名列（批量查免 N+1）
        Map<Long, String> teamNames = teamQuery.loadTeamNames(collectTeamIds(achievements));
        //4.xlsx 流式回写
        writeExcel(achievements, teamNames, response);
    }

    /** POI 写 xlsx：表头 + 每成果一行；文件名带时间戳 */
    private void writeExcel(List<Achievement> achievements, Map<Long, String> teamNames,
                            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = "获奖导出_" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("获奖清单");
                String[] headers = {"成果名称", "类型", "等级", "获奖日期", "归属人", "队伍"};
                XSSFRow headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }
                int rowIdx = 1;
                for (Achievement achievement : achievements) {
                    XSSFRow row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(achievement.getName());
                    row.createCell(1).setCellValue(achievement.getAchievementType());
                    row.createCell(2).setCellValue(nullToEmpty(achievement.getLevel()));
                    row.createCell(3).setCellValue(achievement.getAwardDate() == null
                            ? "" : achievement.getAwardDate().toString());
                    row.createCell(4).setCellValue(nullToEmpty(achievement.getOwnerName()));
                    row.createCell(5).setCellValue(achievement.getTeamId() == null
                            ? "" : nullToEmpty(teamNames.get(achievement.getTeamId())));
                }
                //输出流由容器管理，这里只关工作簿
                workbook.write(response.getOutputStream());
            }
        } catch (IOException e) {
            log.error("获奖导出写流失败：{}", e.getMessage());
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    /** 收集非空 teamId 去重（查队伍名用） */
    private List<Long> collectTeamIds(List<Achievement> achievements) {
        Set<Long> ids = new HashSet<>();
        for (Achievement achievement : achievements) {
            if (achievement.getTeamId() != null) {
                ids.add(achievement.getTeamId());
            }
        }
        return new ArrayList<>(ids);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
