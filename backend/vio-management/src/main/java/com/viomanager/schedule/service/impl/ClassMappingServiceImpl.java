package com.viomanager.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ScheduleErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.schedule.entity.ClassMapping;
import com.viomanager.schedule.entity.CourseTimetable;
import com.viomanager.schedule.mapper.ClassMappingMapper;
import com.viomanager.schedule.mapper.CourseTimetableMapper;
import com.viomanager.schedule.service.ClassMappingService;
import com.viomanager.schedule.support.ClassNameMatcher;
import com.viomanager.schedule.support.FreeSlotCalcSupport;
import com.viomanager.schedule.support.ScheduleMemberSupport;
import com.viomanager.schedule.support.SemesterSupport;
import com.viomanager.schedule.vo.ClassMappingVO;
import com.viomanager.schedule.vo.CourseRecordVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 班级映射与课表查询实现。
 * 可见性口径：课表与映射是全平台共享数据（学校课表不分部门），管理端（controller 已挡学生）
 * 不再按组织子树裁剪——部门管理员确认映射不影响其他部门（确认动作幂等）。
 */
@Service
public class ClassMappingServiceImpl implements ClassMappingService {

    @Resource
    private ClassMappingMapper classMappingMapper;

    @Resource
    private CourseTimetableMapper courseMapper;

    @Resource
    private SemesterSupport semesterSupport;

    @Resource
    private ScheduleMemberSupport memberSupport;

    @Override
    public List<ClassMappingVO> list(Long semesterId, Boolean confirmed, Boolean hasMember) {
        semesterSupport.requireSemester(semesterId);
        //先物化标志位：wrapper 的三参 eq 会无条件求值第三参，Boolean 拆箱在 confirmed=null 时 NPE
        Integer confirmedFlag = confirmed == null ? null : (confirmed ? 1 : 0);
        List<ClassMapping> rows = classMappingMapper.selectList(new LambdaQueryWrapper<ClassMapping>()
                .eq(ClassMapping::getSemesterId, semesterId)
                .eq(confirmedFlag != null, ClassMapping::getConfirmed, confirmedFlag)
                .orderByAsc(ClassMapping::getClassKey));
        //读取时实时匹配在册成员（后续录入的成员即时生效；导入时的 memberClassName 只是快照不参与展示）
        List<ScheduleMemberSupport.MemberBasic> members = memberSupport.allActiveWithClass();
        Set<String> memberClassNames = new LinkedHashSet<>();
        Map<String, ScheduleMemberSupport.MemberBasic> memberByClass = new LinkedHashMap<>();
        for (ScheduleMemberSupport.MemberBasic member : members) {
            memberClassNames.add(member.className());
            memberByClass.putIfAbsent(member.className(), member);
        }
        List<ClassMappingVO> result = new ArrayList<>();
        for (ClassMapping row : rows) {
            String liveClassName = ClassNameMatcher.uniqueMatch(row.getClassKey(), memberClassNames);
            if (Boolean.TRUE.equals(hasMember) && liveClassName == null) {
                continue;
            }
            ScheduleMemberSupport.MemberBasic liveMember = liveClassName == null ? null : memberByClass.get(liveClassName);
            String[] grade = liveMember == null
                    ? new String[]{row.getGrade(), row.getGradeSource()}
                    : FreeSlotCalcSupport.deriveGrade(liveMember.studentNo(), row);
            ClassMappingVO vo = new ClassMappingVO();
            vo.setId(row.getId());
            vo.setSemesterId(row.getSemesterId());
            vo.setClassKey(row.getClassNameRaw());
            vo.setClassName(liveClassName);
            vo.setGrade(grade[0]);
            vo.setGradeSource(grade[1]);
            vo.setConfirmed(row.getConfirmed() != null && row.getConfirmed() == 1);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(List<Long> ids) {
        CurrentUser user = UserHolder.getUser();
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            ClassMapping mapping = classMappingMapper.selectById(id);
            if (mapping == null) {
                throw new BizException(ScheduleErrorCode.CLASS_MAPPING_NOT_FOUND, "班级映射不存在：" + id);
            }
            if (mapping.getConfirmed() != null && mapping.getConfirmed() == 1) {
                continue;
            }
            mapping.setConfirmed(1)
                    .setConfirmedBy(user.getUserId())
                    .setConfirmedAt(now);
            classMappingMapper.updateById(mapping);
        }
    }

    @Override
    public List<CourseRecordVO> courses(Long semesterId, Integer weekNo, String classKey) {
        semesterSupport.requireSemester(semesterId);
        List<CourseTimetable> rows = courseMapper.selectList(new LambdaQueryWrapper<CourseTimetable>()
                .eq(CourseTimetable::getSemesterId, semesterId)
                .eq(weekNo != null, CourseTimetable::getWeekNo, weekNo)
                .eq(classKey != null && !classKey.isBlank(), CourseTimetable::getClassKey, classKey)
                .orderByAsc(CourseTimetable::getWeekNo)
                .orderByAsc(CourseTimetable::getWeekday)
                .orderByAsc(CourseTimetable::getSlotIndex));
        List<CourseRecordVO> result = new ArrayList<>();
        for (CourseTimetable row : rows) {
            CourseRecordVO vo = new CourseRecordVO();
            vo.setId(row.getId());
            vo.setWeekNo(row.getWeekNo());
            vo.setWeekday(row.getWeekday());
            vo.setSlotIndex(row.getSlotIndex());
            vo.setClassKey(row.getClassKey());
            vo.setCourseName(row.getCourseName());
            vo.setTeacher(row.getTeacherName());
            vo.setLocation(row.getClassroom());
            vo.setWeekParity(row.getWeekType());
            result.add(vo);
        }
        return result;
    }
}
