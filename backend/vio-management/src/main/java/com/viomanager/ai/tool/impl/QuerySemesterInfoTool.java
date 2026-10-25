package com.viomanager.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.semester.service.SemesterService;
import com.viomanager.semester.vo.SemesterVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 白名单工具：学期信息（清单 §2 补充，登录即可）。返回当前激活学期 + 实时算出的当前周次
 * + 近期学期列表。模型此前问"现在第几周"只能反问用户——本工具补上这层数据。
 * 周次计算：startDate 到今天满 7 天进一周，越界按未开学/已结束如实标注，不硬造周次。
 */
@Component
public class QuerySemesterInfoTool implements AiTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private SemesterService semesterService;

    @Override
    public String name() {
        return "querySemesterInfo";
    }

    @Override
    public String description() {
        return "查询学期信息：当前激活学期（名称、起止日期、总周数）与今天处于第几周，"
                + "另附近期学期列表；问\"现在是第几周\"\"这学期什么时候结束\"时用本工具，无需参数";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override
    public String execute(JsonNode args, CurrentUser user) throws Exception {
        List<SemesterVO> semesters = semesterService.list();
        ObjectNode root = objectMapper.createObjectNode();
        SemesterVO current = semesters.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsCurrent()))
                .findFirst().orElse(null);
        if (current == null) {
            root.put("error", "当前没有激活学期");
            root.set("semesters", objectMapper.valueToTree(semesters));
            return objectMapper.writeValueAsString(root);
        }
        ObjectNode cur = root.putObject("currentSemester");
        cur.put("name", current.getName());
        cur.put("startDate", current.getStartDate() == null ? null : current.getStartDate().toString());
        cur.put("endDate", current.getEndDate() == null ? null : current.getEndDate().toString());
        cur.put("totalWeeks", current.getTotalWeeks());
        cur.put("status", current.getStatus());
        LocalDate today = LocalDate.now();
        if (current.getStartDate() != null && !today.isBefore(current.getStartDate())) {
            long days = ChronoUnit.DAYS.between(current.getStartDate(), today);
            int week = (int) (days / 7) + 1;
            if (current.getTotalWeeks() != null && week > current.getTotalWeeks()) {
                root.put("currentWeek", current.getTotalWeeks());
                root.put("note", "今天已超出学期总周数，学期可能已结束");
            } else {
                root.put("currentWeek", week);
            }
        } else {
            root.put("note", "今天在学期开始日期之前，学期尚未开学");
        }
        root.put("today", today.toString());
        root.set("semesters", objectMapper.valueToTree(semesters));
        return objectMapper.writeValueAsString(root);
    }
}
