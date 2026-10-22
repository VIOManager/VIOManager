package com.viomanager.schedule.support;

import com.viomanager.common.enums.ScheduleErrorCode;
import com.viomanager.common.exception.BizException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 教务系统「排课结果一览表」适配（课表导入协调拍板：学校真实导出与站内模板是两套表头，不改表靠展开消化）。
 * 三处形态差异：班级一格多班逗号分隔、周次为区间/列表（含「单周/双周」后缀）、节次有跨大节（第1-8节）。
 * 按表头名取列（不按位置），学校改列序不影响；展开为站内口径一行 = 一班 × 一周次 × 一大节。
 */
@Component
public class SchoolExportSupport {

    /** 识别教务导出的特征表头（同时出现即判教务格式，交本类处理）。 */
    static final Set<String> SCHOOL_MARKERS = Set.of("上课周次", "班级", "上课节次");

    /** 教务格式必在列：缺失判文件不合法。 */
    private static final List<String> REQUIRED = List.of(
            "学年学期", "班级", "课程名", "上课周次", "上课星期", "上课节次");

    /** 周次片段：单个「5」或区间「1-16」。 */
    private static final Pattern WEEK_TOKEN = Pattern.compile("^(\\d{1,2})(?:-(\\d{1,2}))?$");

    /** 节次：「第1-2节」，起止均为奇数的大节边界。 */
    private static final Pattern SLOT_RANGE = Pattern.compile("^第(\\d{1,2})-(\\d{1,2})节$");

    /** 展开记录上限：防误传/恶意大文件撑爆内存（真实全校课表展开约 10 万条）。 */
    private static final int MAX_EXPANDED_ROWS = 300_000;

    /** 解析教务导出（由 TimetableExcelSupport 识别格式后转入）。 */
    public TimetableExcelSupport.ParsedTimetable parseSchool(Sheet sheet, DataFormatter fmt) {
        Row header = sheet.getRow(0);
        Map<String, Integer> col = new HashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String name = fmt.formatCellValue(header.getCell(c)).trim();
            if (!name.isEmpty()) {
                col.put(name, c);
            }
        }
        for (String required : REQUIRED) {
            if (!col.containsKey(required)) {
                throw new BizException(ScheduleErrorCode.PARSE_FAILED, "教务导出表头缺少列：" + required);
            }
        }
        List<TimetableExcelSupport.ParsedRow> rows = new ArrayList<>();
        List<TimetableExcelSupport.RowError> errors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String semesterRawName = null;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isBlank(row, fmt, col)) {
                continue;
            }
            int rowNo = r + 1;
            try {
                String semester = cell(row, fmt, col, "学年学期");
                if (semester == null || semester.isBlank()) {
                    throw new IllegalArgumentException("学年学期为空");
                }
                if (semesterRawName == null) {
                    semesterRawName = semester;
                } else if (!semesterRawName.equals(semester)) {
                    throw new IllegalArgumentException("学年学期与首行不一致，一个文件只能是一个学期的课表");
                }
                List<String> classes = splitClasses(cell(row, fmt, col, "班级"));
                if (classes.isEmpty()) {
                    throw new IllegalArgumentException("班级为空");
                }
                int weekday = intCell(row, fmt, col, "上课星期");
                List<Integer> weeks = parseWeeks(cell(row, fmt, col, "上课周次"));
                if (weeks == null) {
                    throw new IllegalArgumentException("上课周次无法解析：" + cell(row, fmt, col, "上课周次"));
                }
                String slotName = cell(row, fmt, col, "上课节次");
                List<Integer> slots = parseSlots(slotName);
                if (slots == null) {
                    throw new IllegalArgumentException("上课节次无法解析：" + slotName);
                }
                String course = cell(row, fmt, col, "课程名");
                String teacher = cell(row, fmt, col, "上课教师名");
                String room = cell(row, fmt, col, "上课地点");
                for (String className : classes) {
                    String classKey = NameNormalizer.classKey(className);
                    for (int weekNo : weeks) {
                        for (int slotIndex : slots) {
                            String dedupe = classKey + "|" + weekNo + "|" + weekday + "|" + slotIndex + "|" + course;
                            if (seen.add(dedupe)) {
                                rows.add(new TimetableExcelSupport.ParsedRow(className, classKey, weekNo,
                                        weekday, slotIndex, slotName, course, teacher, room, "ALL"));
                            }
                        }
                    }
                }
                if (rows.size() > MAX_EXPANDED_ROWS) {
                    throw new BizException(ScheduleErrorCode.PARSE_FAILED,
                            "展开记录数超过上限 " + MAX_EXPANDED_ROWS + "，请确认文件内容");
                }
            } catch (IllegalArgumentException e) {
                errors.add(new TimetableExcelSupport.RowError(rowNo, e.getMessage()));
            }
        }
        return new TimetableExcelSupport.ParsedTimetable(rows, errors, semesterRawName);
    }

    /** 周次解析：「1-11,13-17周」「第10周」「1-8周单周/双周」「2,4,6,8周」→ 显式周次列表；不可解析返回 null。 */
    List<Integer> parseWeeks(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        boolean oddOnly = false;
        boolean evenOnly = false;
        if (s.endsWith("单周")) {
            oddOnly = true;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("双周")) {
            evenOnly = true;
            s = s.substring(0, s.length() - 2);
        }
        s = s.replace("周", "").replace("第", "").trim();
        Set<Integer> weeks = new LinkedHashSet<>();
        for (String token : s.split("[,，、]")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            Matcher matcher = WEEK_TOKEN.matcher(token);
            if (!matcher.matches()) {
                return null;
            }
            int from = Integer.parseInt(matcher.group(1));
            int to = matcher.group(2) == null ? from : Integer.parseInt(matcher.group(2));
            if (from < 1 || to < from || to > 60) {
                return null;
            }
            for (int w = from; w <= to; w++) {
                weeks.add(w);
            }
        }
        if (weeks.isEmpty()) {
            return null;
        }
        if (oddOnly) {
            weeks.removeIf(w -> w % 2 == 0);
        } else if (evenOnly) {
            weeks.removeIf(w -> w % 2 == 1);
        }
        return weeks.isEmpty() ? null : new ArrayList<>(weeks);
    }

    /** 节次解析：「第1-2节」→[1]、「第1-4节」→[1,2]、「第1-8节」→[1,2,3,4]、「第9-10节」→[5]；不可解析返回 null。 */
    List<Integer> parseSlots(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = SLOT_RANGE.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        int from = Integer.parseInt(matcher.group(1));
        int to = Integer.parseInt(matcher.group(2));
        if (from < 1 || from % 2 != 1 || to < from || to > 10) {
            return null;
        }
        List<Integer> slots = new ArrayList<>();
        for (int n = from; n <= to; n += 2) {
            int slot = (n + 1) / 2;
            if (slot > 5) {
                return null;
            }
            slots.add(slot);
        }
        return slots.isEmpty() ? null : slots;
    }

    private List<String> splitClasses(String raw) {
        List<String> classes = new ArrayList<>();
        if (raw == null) {
            return classes;
        }
        for (String token : raw.split("[,，、]")) {
            token = token.trim();
            if (!token.isEmpty()) {
                classes.add(token);
            }
        }
        return classes;
    }

    private int intCell(Row row, DataFormatter fmt, Map<String, Integer> col, String name) {
        String raw = cell(row, fmt, col, name);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(name + "为空");
        }
        try {
            int value = (int) Double.parseDouble(raw.trim());
            if (value < 1 || value > 7) {
                throw new IllegalArgumentException(name + "须在 1–7 内");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + "须为数字，当前：" + raw);
        }
    }

    private String cell(Row row, DataFormatter fmt, Map<String, Integer> col, String name) {
        Integer index = col.get(name);
        if (index == null) {
            return null;
        }
        String value = fmt.formatCellValue(row.getCell(index));
        return value == null ? null : value.trim();
    }

    private boolean isBlank(Row row, DataFormatter fmt, Map<String, Integer> col) {
        for (String required : REQUIRED) {
            String value = cell(row, fmt, col, required);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
