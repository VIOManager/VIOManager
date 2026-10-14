package com.viomanager.schedule.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.schedule.entity.ClassMapping;
import com.viomanager.schedule.entity.CourseTimetable;
import com.viomanager.schedule.mapper.ClassMappingMapper;
import com.viomanager.schedule.mapper.CourseTimetableMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 空课计算引擎（SC06 矩阵 / SC07 名单 / SC02 课表冲突共用同源逻辑，AI 助手复用同规则）。
 * 判定口径（接口清单 §3）：confirmed=1 的 class_mapping 才参与（PRD 铁规则）；
 * 某班级在 (weekNo, weekday, slotIndex) 无课记录即空课；ODD 仅奇数周占用、EVEN 仅偶数周、
 * ALL 每周；同格多门课取并集。成员归属 = normalized(member.class_name) ∈ 已确认 class_key。
 */
@Component
public class FreeSlotCalcSupport {

    /** 占用格键：classKey + 星期 + 节次。 */
    public record OccupyKey(String classKey, int weekday, int slotIndex) {
    }

    /** 空课名单行：含年级推导结果（学号前 4 位优先，班级映射年级兜底）。 */
    public record FreeMemberRow(Long memberId, String name, String className, String classKey,
                                String grade, String gradeSource) {
    }

    @Resource
    private ClassMappingMapper classMappingMapper;

    @Resource
    private CourseTimetableMapper courseTimetableMapper;

    @Resource
    private ScheduleMemberSupport memberSupport;

    /** 已确认映射列表（semesterId 维度）。 */
    public List<ClassMapping> confirmedMappings(Long semesterId) {
        return classMappingMapper.selectList(new LambdaQueryWrapper<ClassMapping>()
                .eq(ClassMapping::getSemesterId, semesterId)
                .eq(ClassMapping::getConfirmed, 1));
    }

    /** 该学期已确认班级的全部在册成员（年级已推导）。 */
    public List<FreeMemberRow> freeMemberCandidates(Long semesterId) {
        List<ClassMapping> confirmed = confirmedMappings(semesterId);
        Map<String, ClassMapping> byKey = new HashMap<>();
        for (ClassMapping mapping : confirmed) {
            byKey.put(mapping.getClassKey(), mapping);
        }
        List<FreeMemberRow> result = new ArrayList<>();
        for (ScheduleMemberSupport.MemberBasic member : memberSupport.allActiveWithClass()) {
            ClassMapping mapping = byKey.get(NameNormalizer.classKey(member.className()));
            if (mapping == null) {
                continue;
            }
            String[] grade = deriveGrade(member.studentNo(), mapping);
            result.add(new FreeMemberRow(member.id(), member.name(), member.className(),
                    mapping.getClassKey(), grade[0], grade[1]));
        }
        return result;
    }

    /** 该 (学期, 周次) 的占用格集合（单双周已按周次奇偶过滤）。 */
    public Set<OccupyKey> occupiedCells(Long semesterId, int weekNo) {
        boolean oddWeek = weekNo % 2 == 1;
        List<CourseTimetable> courses = courseTimetableMapper.selectList(
                new LambdaQueryWrapper<CourseTimetable>()
                        .eq(CourseTimetable::getSemesterId, semesterId)
                        .eq(CourseTimetable::getWeekNo, weekNo));
        Set<OccupyKey> occupied = new HashSet<>();
        for (CourseTimetable course : courses) {
            boolean occupies = switch (course.getWeekType() == null ? "ALL" : course.getWeekType()) {
                case "ODD" -> oddWeek;
                case "EVEN" -> !oddWeek;
                default -> true;
            };
            if (occupies) {
                occupied.add(new OccupyKey(course.getClassKey(), course.getWeekday(), course.getSlotIndex()));
            }
        }
        return occupied;
    }

    /** 该人该格是否空课（classKey 命中占用集即有课）。 */
    public boolean isFree(Set<OccupyKey> occupied, String classKey, int weekday, int slotIndex) {
        return !occupied.contains(new OccupyKey(classKey, weekday, slotIndex));
    }

    /**
     * 冲突课程探查（SC02 校验链第 5 步）：有课返回冲突提示文案（「第3-4节有课：高等数学」），空课返回 null。
     */
    public String conflictNote(Long semesterId, int weekNo, String classKey, int weekday, int slotIndex) {
        boolean oddWeek = weekNo % 2 == 1;
        List<CourseTimetable> courses = courseTimetableMapper.selectList(
                new LambdaQueryWrapper<CourseTimetable>()
                        .eq(CourseTimetable::getSemesterId, semesterId)
                        .eq(CourseTimetable::getWeekNo, weekNo)
                        .eq(CourseTimetable::getClassKey, classKey)
                        .eq(CourseTimetable::getWeekday, weekday)
                        .eq(CourseTimetable::getSlotIndex, slotIndex));
        for (CourseTimetable course : courses) {
            boolean occupies = switch (course.getWeekType() == null ? "ALL" : course.getWeekType()) {
                case "ODD" -> oddWeek;
                case "EVEN" -> !oddWeek;
                default -> true;
            };
            if (occupies) {
                String slot = course.getSlotName() == null ? "该时段" : course.getSlotName();
                return slot + "有课：" + (course.getCourseName() == null ? "未命名课程" : course.getCourseName());
            }
        }
        return null;
    }

    /** 年级推导：学号前 4 位数字优先（STUDENT_NO），否则班级映射上的年级（CLASS_NAME），再否则空。 */
    public static String[] deriveGrade(String studentNo, ClassMapping mapping) {
        if (studentNo != null && studentNo.length() >= 4) {
            String head = studentNo.substring(0, 4);
            if (head.chars().allMatch(Character::isDigit)) {
                return new String[]{head, "STUDENT_NO"};
            }
        }
        if (mapping != null && mapping.getGrade() != null && !mapping.getGrade().isBlank()) {
            return new String[]{mapping.getGrade(), "CLASS_NAME"};
        }
        return new String[]{null, null};
    }
}
