package com.viomanager.schedule.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.viomanager.schedule.entity.ClassMapping;
import com.viomanager.schedule.entity.TimetableImport;
import com.viomanager.schedule.mapper.ClassMappingMapper;
import com.viomanager.schedule.mapper.TimetableImportMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 课表解析结果落库支撑（TT02 命中学期 / TT05 续跑共用，从 TimetableImportServiceImpl 拆出以守 300 行铁律）。
 * 职责：课表行 → course_timetable（分片批量 INSERT：全校课表展开近 10 万条，逐条写会超上传超时）；
 * 班级去重 upsert → class_mapping（confirmed=0 等 TT07 确认），档案班级名用 ClassNameMatcher 宽容匹配
 * 建议（教务「软件23-1班」↔档案「软件2301班」），年级按成员学号优先、班级名 4 位年号兜底。
 */
@Component
public class TimetableWriteSupport {

    /** 批量插入分片大小（multi-VALUES 行数）。 */
    private static final int INSERT_CHUNK = 500;

    @Resource
    private ClassMappingMapper classMappingMapper;

    @Resource
    private TimetableImportMapper importMapper;

    @Resource
    private ScheduleMemberSupport memberSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 写课表行与班级映射，并置批次 MAPPING（FAILED 批次不动状态）。 */
    public void writeParsed(TimetableImport batch, Long semesterId, TimetableExcelSupport.ParsedTimetable parsed) {
        insertCourses(batch, semesterId, parsed.rows());
        upsertClassMappings(batch, semesterId, parsed);
        if (!"FAILED".equals(batch.getStatus())) {
            batch.setStatus("MAPPING");
            importMapper.updateById(batch);
        }
    }

    /** 课表行批量落库（显式列名；id 用 IdWorker 雪花与 MyBatis-Plus ASSIGN_ID 同源）。 */
    private void insertCourses(TimetableImport batch, Long semesterId,
                               List<TimetableExcelSupport.ParsedRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        String head = "INSERT INTO course_timetable (id, import_id, semester_id, class_name, class_key, "
                + "week_no, weekday, slot_index, slot_name, course_name, teacher_name, classroom, week_type, org_id) VALUES ";
        for (int from = 0; from < rows.size(); from += INSERT_CHUNK) {
            List<TimetableExcelSupport.ParsedRow> part =
                    rows.subList(from, Math.min(from + INSERT_CHUNK, rows.size()));
            StringBuilder sql = new StringBuilder(head);
            List<Object> args = new ArrayList<>(part.size() * 14);
            for (TimetableExcelSupport.ParsedRow row : part) {
                sql.append("(?,?,?,?,?,?,?,?,?,?,?,?,?,?),");
                args.add(IdWorker.getId());
                args.add(batch.getId());
                args.add(semesterId);
                args.add(row.className());
                args.add(row.classKey());
                args.add(row.weekNo());
                args.add(row.weekday());
                args.add(row.slotIndex());
                args.add(row.slotName());
                args.add(row.courseName());
                args.add(row.teacherName());
                args.add(row.classroom());
                args.add(row.weekType() == null ? "ALL" : row.weekType());
                args.add(batch.getOrgId());
            }
            jdbcTemplate.update(sql.deleteCharAt(sql.length() - 1).toString(), args.toArray());
        }
    }

    /** 班级去重写 class_mapping：同 (semester, classKey) 已存在跳过。 */
    private void upsertClassMappings(TimetableImport batch, Long semesterId,
                                     TimetableExcelSupport.ParsedTimetable parsed) {
        Map<String, String> rawByClassKey = new LinkedHashMap<>();
        parsed.rows().forEach(row -> rawByClassKey.putIfAbsent(row.classKey(), row.className()));
        Map<String, ClassMapping> existingByKey = new LinkedHashMap<>();
        classMappingMapper.selectList(new LambdaQueryWrapper<ClassMapping>()
                        .eq(ClassMapping::getSemesterId, semesterId))
                .forEach(mapping -> existingByKey.put(mapping.getClassKey(), mapping));
        //成员档案整批读一次：506 个班级共享，避免逐班级全量查库
        List<ScheduleMemberSupport.MemberBasic> members = memberSupport.allActiveWithClass();
        Set<String> memberClassNames = new LinkedHashSet<>();
        members.forEach(member -> memberClassNames.add(member.className()));
        rawByClassKey.forEach((classKey, rawName) -> {
            if (existingByKey.containsKey(classKey)) {
                return;
            }
            String memberClassName = ClassNameMatcher.uniqueMatch(classKey, memberClassNames);
            ScheduleMemberSupport.MemberBasic anyMember = memberClassName == null ? null
                    : members.stream()
                            .filter(member -> memberClassName.equals(member.className()))
                            .findFirst().orElse(null);
            ClassMapping mapping = new ClassMapping()
                    .setSemesterId(semesterId)
                    .setImportId(batch.getId())
                    .setClassKey(classKey)
                    .setClassNameRaw(rawName)
                    .setMemberClassName(memberClassName)
                    .setConfirmed(0)
                    .setOrgId(batch.getOrgId());
            String[] grade = anyMember == null ? new String[]{null, null}
                    : FreeSlotCalcSupport.deriveGrade(anyMember.studentNo(), null);
            if (grade[0] == null) {
                String fromName = digitsFromName(rawName);
                if (fromName != null) {
                    grade = new String[]{fromName, "CLASS_NAME"};
                }
            }
            if (grade[0] != null) {
                mapping.setGrade(grade[0]).setGradeSource(grade[1]);
            }
            classMappingMapper.insert(mapping);
        });
    }

    /** 班级名里提 4 位年级（19xx/20xx 开头，如「2022级计科」→ 2022）；「计科22-1」这类两位写法不猜，返回 null。 */
    private String digitsFromName(String name) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(name);
        return matcher.find() ? matcher.group() : null;
    }
}
