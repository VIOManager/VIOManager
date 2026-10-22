package com.viomanager.schedule.support;

import com.viomanager.common.enums.ScheduleErrorCode;
import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 课表 Excel 支撑：TT01 模板生成 + TT02/TT05 解析（POI）。
 * 模板 16 列定义见《排班绩效模块实现方案》§3.2（契约未定义处由方案定）：
 * 前 10 列 = course_timetable 契约字段 + 学期名称（表头必须有，值可空的列：任课教师/上课地点/节次名称），
 * 后 6 列为辅助信息列（学院/专业/年级/学号起/备注/重复标记，解析忽略不报错）。
 * 解析按**表头名**映射列（不按位置），学校导出列序变化不影响；
 * 教务「排课结果一览表」格式（上课周次/班级/上课节次特征表头）由 SchoolExportSupport 适配展开。
 */
@Component
public class TimetableExcelSupport {

    @Resource
    private SchoolExportSupport schoolExportSupport;

    @Resource
    private CsvWorkbookSupport csvWorkbookSupport;


    /** 模板 16 列表头（顺序即模板列序）。 */
    private static final String[] HEADERS = {
            "学期名称", "班级名称", "课程名称", "任课教师", "上课地点",
            "周次", "星期", "节次序号", "节次名称", "周类型",
            "学院", "专业", "年级", "学号起", "备注", "重复标记"};

    /** 契约必在列：缺失任何一个直接判模板不合法。 */
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "学期名称", "班级名称", "课程名称", "任课教师", "上课地点",
            "周次", "星期", "节次序号", "节次名称", "周类型");

    /** 解析结果行（已归一化 classKey，业务字段齐备）。 */
    public record ParsedRow(String className, String classKey, int weekNo, int weekday, int slotIndex,
                            String slotName, String courseName, String teacherName, String classroom,
                            String weekType) {
    }

    /** 行级错误（TT04 详情直出）。 */
    public record RowError(int rowNo, String reason) {
    }

    /** 解析结果：有效行 + 行级错误 + 文件内学期原始名（首个有效行的值）。 */
    public record ParsedTimetable(List<ParsedRow> rows, List<RowError> errors, String semesterRawName) {
    }

    /** TT01：生成 16 列模板（含 5 条示例行）的 xlsx 字节。 */
    public byte[] buildTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("课表导入");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, 14 * 256);
            }
            String[][] examples = {
                    {"2026-2027学年第一学期", "数媒2301班", "高等数学", "张老师", "A栋101", "1", "1", "1", "第1-2节", "ALL", "数媒学院", "数字媒体", "2023", "", "", ""},
                    {"2026-2027学年第一学期", "数媒2301班", "大学英语", "李老师", "B栋203", "1", "3", "2", "第3-4节", "ODD", "数媒学院", "数字媒体", "2023", "", "", ""},
                    {"2026-2027学年第一学期", "计科2202班", "数据结构", "王老师", "C栋305", "2", "2", "3", "第5-6节", "EVEN", "信息学院", "计算机", "2022", "", "", ""},
                    {"2026-2027学年第一学期", "计科2202班", "操作系统", "赵老师", "C栋308", "3", "4", "4", "第7-8节", "ALL", "信息学院", "计算机", "2022", "", "", ""},
                    {"2026-2027学年第一学期", "机械2401班", "工程制图", "孙老师", "实训楼201", "5", "5", "5", "第9-10节", "ALL", "机械学院", "机械设计", "2024", "", "", ""}};
            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) {
                    row.createCell(c).setCellValue(examples[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ScheduleErrorCode.PARSE_FAILED, "模板生成失败：" + e.getMessage());
        }
    }

    /** TT02/TT05：解析课表文件（xlsx / xls / CSV 教务导出均支持），行级错误不中断整体解析。 */
    public ParsedTimetable parse(InputStream in) {
        try {
            return parse(in.readAllBytes());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ScheduleErrorCode.PARSE_FAILED, "课表文件读取失败：" + e.getMessage());
        }
    }

    /** 嗅探分发：PK 头 = xlsx、D0CF 头 = xls，其余按教务 CSV 导出转 POI 工作簿（CsvWorkbookSupport）。 */
    private Workbook openWorkbook(byte[] bytes) throws java.io.IOException {
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            return new XSSFWorkbook(new ByteArrayInputStream(bytes));
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF) {
            return WorkbookFactory.create(new ByteArrayInputStream(bytes));
        }
        return csvWorkbookSupport.toWorkbook(bytes);
    }

    public ParsedTimetable parse(byte[] bytes) {
        try (Workbook wb = openWorkbook(bytes)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null || sheet.getRow(0) == null) {
                throw new BizException(ScheduleErrorCode.PARSE_FAILED, "文件无表头行");
            }
            DataFormatter fmt = new DataFormatter();
            //1.表头名 → 列号
            java.util.Map<String, Integer> colByName = new java.util.HashMap<>();
            for (int c = 0; c < sheet.getRow(0).getLastCellNum(); c++) {
                String name = fmt.formatCellValue(sheet.getRow(0).getCell(c)).trim();
                if (!name.isEmpty()) {
                    colByName.put(name, c);
                }
            }
            //1.5 教务「排课结果一览表」特征表头齐备 → 交适配器（表头别名 + 行展开）
            if (colByName.keySet().containsAll(SchoolExportSupport.SCHOOL_MARKERS)) {
                return schoolExportSupport.parseSchool(sheet, fmt);
            }
            for (String required : REQUIRED_HEADERS) {
                if (!colByName.containsKey(required)) {
                    throw new BizException(ScheduleErrorCode.PARSE_FAILED, "模板表头缺少列：" + required);
                }
            }
            //2.逐行解析
            List<ParsedRow> rows = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String semesterRawName = null;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, fmt, colByName)) {
                    continue;
                }
                int rowNo = r + 1;
                try {
                    ParsedRow parsed = parseRow(row, fmt, colByName);
                    if (semesterRawName == null) {
                        semesterRawName = cell(row, fmt, colByName, "学期名称");
                    } else if (!semesterRawName.equals(cell(row, fmt, colByName, "学期名称"))) {
                        throw new IllegalArgumentException("学期名称与首行不一致，一个文件只能是一个学期的课表");
                    }
                    String dedupe = parsed.classKey() + "|" + parsed.weekNo() + "|" + parsed.weekday()
                            + "|" + parsed.slotIndex() + "|" + parsed.courseName();
                    if (seen.add(dedupe)) {
                        rows.add(parsed);
                    }
                } catch (IllegalArgumentException e) {
                    errors.add(new RowError(rowNo, e.getMessage()));
                }
            }
            return new ParsedTimetable(rows, errors, semesterRawName);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ScheduleErrorCode.PARSE_FAILED, "课表文件解析失败：" + e.getMessage());
        }
    }

    private ParsedRow parseRow(Row row, DataFormatter fmt, java.util.Map<String, Integer> colByName) {
        String semester = cell(row, fmt, colByName, "学期名称");
        if (semester == null || semester.isBlank()) {
            throw new IllegalArgumentException("学期名称为空");
        }
        String className = cell(row, fmt, colByName, "班级名称");
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("班级名称为空");
        }
        int weekNo = intCell(row, fmt, colByName, "周次", 1, 60);
        int weekday = intCell(row, fmt, colByName, "星期", 1, 7);
        int slotIndex = intCell(row, fmt, colByName, "节次序号", 1, 5);
        String weekType = weekTypeCell(row, fmt, colByName);
        return new ParsedRow(className.trim(), NameNormalizer.classKey(className), weekNo, weekday, slotIndex,
                cell(row, fmt, colByName, "节次名称"), cell(row, fmt, colByName, "课程名称"),
                cell(row, fmt, colByName, "任课教师"), cell(row, fmt, colByName, "上课地点"), weekType);
    }

    /** 周类型：空白=ALL；接受英文枚举与中文别名（每周/单周/双周）。 */
    private String weekTypeCell(Row row, DataFormatter fmt, java.util.Map<String, Integer> colByName) {
        String raw = cell(row, fmt, colByName, "周类型");
        if (raw == null || raw.isBlank()) {
            return "ALL";
        }
        return switch (raw.trim()) {
            case "ALL", "每周" -> "ALL";
            case "ODD", "单周" -> "ODD";
            case "EVEN", "双周" -> "EVEN";
            default -> throw new IllegalArgumentException("周类型须为 ALL/ODD/EVEN（每周/单周/双周），当前：" + raw);
        };
    }

    private int intCell(Row row, DataFormatter fmt, java.util.Map<String, Integer> colByName,
                        String name, int min, int max) {
        String raw = cell(row, fmt, colByName, name);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(name + "为空");
        }
        int value;
        try {
            value = (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + "须为数字，当前：" + raw);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + "须在 " + min + "–" + max + " 内，当前：" + value);
        }
        return value;
    }

    private String cell(Row row, DataFormatter fmt, java.util.Map<String, Integer> colByName, String name) {
        Integer col = colByName.get(name);
        if (col == null) {
            return null;
        }
        String value = fmt.formatCellValue(row.getCell(col));
        return value == null ? null : value.trim();
    }

    private boolean isBlankRow(Row row, DataFormatter fmt, java.util.Map<String, Integer> colByName) {
        for (String required : REQUIRED_HEADERS) {
            String value = cell(row, fmt, colByName, required);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
