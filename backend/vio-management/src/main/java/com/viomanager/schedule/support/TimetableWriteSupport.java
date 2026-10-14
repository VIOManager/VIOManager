package com.viomanager.schedule.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.schedule.entity.ClassMapping;
import com.viomanager.schedule.entity.CourseTimetable;
import com.viomanager.schedule.entity.TimetableImport;
import com.viomanager.schedule.mapper.ClassMappingMapper;
import com.viomanager.schedule.mapper.CourseTimetableMapper;
import com.viomanager.schedule.mapper.TimetableImportMapper;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 课表解析结果落库支撑（TT02 命中学期 / TT05 续跑共用，从 TimetableImportServiceImpl 拆出以守 300 行铁律）。
 * 职责：课表行 → course_timetable；班级去重 upsert → class_mapping（confirmed=0 等 TT07 确认），
 * 自动匹配档案班级名（归一化键）并推导年级（成员学号优先、班级名 4 位年号兜底）。
 */
@Component
public class TimetableWriteSupport {

    @Resource
    private CourseTimetableMapper courseMapper;

    @Resource
    private ClassMappingMapper classMappingMapper;

    @Resource
    private TimetableImportMapper importMapper;

    @Resource
    private ScheduleMemberSupport memberSupport;

    /** 写课表行与班级映射，并置批次 MAPPING（FAILED 批次不动状态）。 */
    public void writeParsed(TimetableImport batch, Long semesterId, TimetableExcelSupport.ParsedTimetable parsed) {
        parsed.rows().forEach(row -> courseMapper.insert(new CourseTimetable()
                .setImportId(batch.getId())
                .setSemesterId(semesterId)
                .setClassName(row.className())
                .setClassKey(row.classKey())
                .setWeekNo(row.weekNo())
                .setWeekday(row.weekday())
                .setSlotIndex(row.slotIndex())
                .setSlotName(row.slotName())
                .setCourseName(row.courseName())
                .setTeacherName(row.teacherName())
                .setClassroom(row.classroom())
                .setWeekType(row.weekType())
                .setOrgId(batch.getOrgId())));
        upsertClassMappings(batch, semesterId, parsed);
        if (!"FAILED".equals(batch.getStatus())) {
            batch.setStatus("MAPPING");
            importMapper.updateById(batch);
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
        rawByClassKey.forEach((classKey, rawName) -> {
            if (existingByKey.containsKey(classKey)) {
                return;
            }
            String memberClassName = matchMemberClassName(classKey);
            ClassMapping mapping = new ClassMapping()
                    .setSemesterId(semesterId)
                    .setImportId(batch.getId())
                    .setClassKey(classKey)
                    .setClassNameRaw(rawName)
                    .setMemberClassName(memberClassName)
                    .setConfirmed(0)
                    .setOrgId(batch.getOrgId());
            ScheduleMemberSupport.MemberBasic anyMember = firstMemberOfClass(classKey);
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

    /** 档案侧班级名匹配：同归一化键取第一个在册成员的班级名。 */
    private String matchMemberClassName(String classKey) {
        ScheduleMemberSupport.MemberBasic member = firstMemberOfClass(classKey);
        return member == null ? null : member.className();
    }

    private ScheduleMemberSupport.MemberBasic firstMemberOfClass(String classKey) {
        for (ScheduleMemberSupport.MemberBasic member : memberSupport.allActiveWithClass()) {
            if (classKey.equals(NameNormalizer.classKey(member.className()))) {
                return member;
            }
        }
        return null;
    }

    /** 班级名里提 4 位年级（19xx/20xx 开头，如「2022级计科」→ 2022）；「计科22-1」这类两位写法不猜，返回 null。 */
    private String digitsFromName(String name) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(name);
        return matcher.find() ? matcher.group() : null;
    }
}
