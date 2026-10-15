package com.viomanager.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ScheduleErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.schedule.dto.ScheduleBatchCreateDTO;
import com.viomanager.schedule.dto.ScheduleUpdateDTO;
import com.viomanager.schedule.entity.Schedule;
import com.viomanager.schedule.entity.ScheduleRule;
import com.viomanager.schedule.mapper.ScheduleMapper;
import com.viomanager.schedule.service.ScheduleRuleService;
import com.viomanager.schedule.service.ScheduleService;
import com.viomanager.schedule.support.FreeSlotCalcSupport;
import com.viomanager.schedule.support.NameNormalizer;
import com.viomanager.schedule.support.ScheduleKeyReleaseSupport;
import com.viomanager.schedule.support.ScheduleMemberSupport;
import com.viomanager.schedule.support.SemesterSupport;
import com.viomanager.schedule.support.SubOrgSupport;
import com.viomanager.schedule.vo.MyScheduleVO;
import com.viomanager.schedule.vo.ScheduleCellVO;
import com.viomanager.schedule.vo.ScheduleEntryVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排班实现。数据范围：超管全部 / 部门管理员本子树（org_id = sub_org_id）/ 学生仅本人（SC05）。
 */
@Service
public class ScheduleServiceImpl implements ScheduleService {

    @Resource
    private ScheduleMapper scheduleMapper;

    @Resource
    private ScheduleRuleService ruleService;

    @Resource
    private SemesterSupport semesterSupport;

    @Resource
    private SubOrgSupport subOrgSupport;

    @Resource
    private ScheduleMemberSupport memberSupport;

    @Resource
    private FreeSlotCalcSupport calcSupport;

    @Resource
    private ScheduleKeyReleaseSupport keyReleaseSupport;

    @Override
    public List<ScheduleCellVO> weekTable(Long semesterId, Integer weekNo, Long subOrgId) {
        semesterSupport.checkWeekInRange(semesterId, weekNo, ScheduleErrorCode.WEEK_OUT_OF_RANGE);
        subOrgSupport.requireSubOrg(subOrgId);
        checkSubOrgVisible(subOrgId);
        Map<String, List<ScheduleEntryVO>> byCell = new LinkedHashMap<>();
        loadWeek(semesterId, weekNo, subOrgId).forEach(schedule -> byCell
                .computeIfAbsent(cellKey(schedule.getWeekday(), schedule.getSlotIndex()), k -> new ArrayList<>())
                .add(toEntry(schedule)));
        List<ScheduleCellVO> result = new ArrayList<>(35);
        for (int weekday = 1; weekday <= 7; weekday++) {
            for (int slotIndex = 1; slotIndex <= 5; slotIndex++) {
                ScheduleCellVO cell = new ScheduleCellVO();
                cell.setWeekday(weekday);
                cell.setSlotIndex(slotIndex);
                cell.setEntries(byCell.getOrDefault(cellKey(weekday, slotIndex), List.of()));
                result.add(cell);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createBatch(ScheduleBatchCreateDTO dto) {
        semesterSupport.checkWeekInRange(dto.getSemesterId(), dto.getWeekNo(), ScheduleErrorCode.WEEK_OUT_OF_RANGE);
        subOrgSupport.requireSubOrg(dto.getSubOrgId());
        checkSubOrgVisible(dto.getSubOrgId());
        Map<String, ScheduleRule> ruleMap = ruleService.effectiveRuleMap(dto.getSubOrgId(), dto.getSemesterId());
        //已有排班（PLANNED）：8005 按人、8006 按格计数
        List<Schedule> existing = loadWeek(dto.getSemesterId(), dto.getWeekNo(), dto.getSubOrgId());
        Set<Long> scheduledMembers = new HashSet<>();
        Map<String, Integer> slotCount = new HashMap<>();
        existing.forEach(s -> {
            scheduledMembers.add(s.getMemberId());
            slotCount.merge(cellKey(s.getWeekday(), s.getSlotIndex()), 1, Integer::sum);
        });
        Map<Long, ScheduleMemberSupport.MemberBasic> members = memberSupport
                .membersByIds(dto.getAssignments().stream().map(ScheduleBatchCreateDTO.Assignment::getMemberId).toList());
        Set<Long> batchMembers = new HashSet<>();
        for (ScheduleBatchCreateDTO.Assignment assignment : dto.getAssignments()) {
            //1.人员属于该子组织（8009）
            ScheduleMemberSupport.MemberBasic member = members.get(assignment.getMemberId());
            if (member == null) {
                throw new BizException(CommonErrorCode.NOT_FOUND, "人员不存在：" + assignment.getMemberId());
            }
            if (!dto.getSubOrgId().equals(member.orgId())) {
                throw new BizException(ScheduleErrorCode.MEMBER_NOT_IN_SUB_ORG,
                        "人员「" + member.name() + "」不属于该子组织");
            }
            String cellKey = cellKey(assignment.getWeekday(), assignment.getSlotIndex());
            //2.时段已启用（8007）
            ScheduleRule rule = ruleMap.get(ruleKey(assignment.getWeekday(), assignment.getSlotIndex()));
            if (rule == null || rule.getEnabled() == null || rule.getEnabled() != 1) {
                throw new BizException(ScheduleErrorCode.SLOT_NOT_ENABLED, "该时段未启用排班");
            }
            //3.人数未超上限（8006，含本批次内已计）
            if (slotCount.getOrDefault(cellKey, 0) >= rule.getMaxPerson()) {
                throw new BizException(ScheduleErrorCode.SLOT_FULL,
                        "该时段排班人数已达上限（" + rule.getMaxPerson() + " 人）");
            }
            //4.同周同人同子组织仅一条（8005，拍板①：服务层兜底唯一键缺口）
            if (scheduledMembers.contains(assignment.getMemberId()) || !batchMembers.add(assignment.getMemberId())) {
                throw new BizException(ScheduleErrorCode.DUPLICATE_SCHEDULE_IN_WEEK,
                        "人员「" + member.name() + "」该周在该子组织已有排班");
            }
            //5.课表冲突（8008：force=true 保存并打标记）
            String classKey = NameNormalizer.classKey(member.className());
            String conflictNote = member.className() == null ? null
                    : calcSupport.conflictNote(dto.getSemesterId(), dto.getWeekNo(), classKey,
                            assignment.getWeekday(), assignment.getSlotIndex());
            if (conflictNote != null && !Boolean.TRUE.equals(dto.getForce())) {
                throw new BizException(ScheduleErrorCode.COURSE_CONFLICT,
                        "人员「" + member.name() + "」" + conflictNote + "，确认强制保存请重试并携带 force");
            }
            //同格可能被本人已取消的行占键，落库前释放（uk_schedule_conflict 尾列是 deleted）
            keyReleaseSupport.releaseSuperseded(dto.getSemesterId(), dto.getWeekNo(), dto.getSubOrgId(),
                    member.id(), assignment.getSlotIndex());
            scheduleMapper.insert(new Schedule()
                    .setSemesterId(dto.getSemesterId())
                    .setWeekNo(dto.getWeekNo())
                    .setSubOrgId(dto.getSubOrgId())
                    .setMemberId(member.id())
                    .setMemberName(member.name())
                    .setMemberClassName(member.className())
                    .setWeekday(assignment.getWeekday())
                    .setSlotIndex(assignment.getSlotIndex())
                    .setDutyName(assignment.getDutyName())
                    .setCourseConflict(conflictNote != null ? 1 : 0)
                    .setConflictNote(conflictNote)
                    .setStatus("PLANNED")
                    .setOrgId(dto.getSubOrgId()));
            slotCount.merge(cellKey, 1, Integer::sum);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ScheduleUpdateDTO dto) {
        Schedule schedule = requireOwnedSchedule(id);
        if (!"PLANNED".equals(schedule.getStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "已取消的排班不可编辑");
        }
        int weekday = dto.getWeekday() != null ? dto.getWeekday() : schedule.getWeekday();
        int slotIndex = dto.getSlotIndex() != null ? dto.getSlotIndex() : schedule.getSlotIndex();
        boolean slotChanged = weekday != schedule.getWeekday() || slotIndex != schedule.getSlotIndex();
        //改时段才重过 8007/8006/8005/8008（排除自身）；仅改文案不动校验
        if (slotChanged) {
            validateTargetSlot(schedule, weekday, slotIndex);
            keyReleaseSupport.releaseSuperseded(schedule.getSemesterId(), schedule.getWeekNo(),
                    schedule.getSubOrgId(), schedule.getMemberId(), slotIndex);
        }
        schedule.setWeekday(weekday)
                .setSlotIndex(slotIndex)
                .setDutyName(dto.getDutyName() != null ? dto.getDutyName() : schedule.getDutyName())
                .setRemark(dto.getRemark() != null ? dto.getRemark() : schedule.getRemark());
        scheduleMapper.updateById(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        Schedule schedule = requireOwnedSchedule(id);
        if ("CANCELLED".equals(schedule.getStatus())) {
            return;
        }
        schedule.setStatus("CANCELLED");
        scheduleMapper.updateById(schedule);
    }

    @Override
    public List<MyScheduleVO> my(Long semesterId, Integer weekNo) {
        Long memberId = memberSupport.requireCurrentMemberId();
        List<Schedule> rows = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMemberId, memberId)
                .eq(semesterId != null, Schedule::getSemesterId, semesterId)
                .eq(weekNo != null, Schedule::getWeekNo, weekNo)
                .eq(Schedule::getStatus, "PLANNED")
                .orderByAsc(Schedule::getWeekNo)
                .orderByAsc(Schedule::getWeekday)
                .orderByAsc(Schedule::getSlotIndex));
        Map<Long, String> orgNames = subOrgSupport.orgNames(
                rows.stream().map(Schedule::getSubOrgId).distinct().toList());
        return rows.stream().map(row -> {
            MyScheduleVO vo = new MyScheduleVO();
            vo.setId(row.getId());
            vo.setWeekNo(row.getWeekNo());
            vo.setSubOrgName(orgNames.getOrDefault(row.getSubOrgId(), "未知组织"));
            vo.setWeekday(row.getWeekday());
            vo.setSlotIndex(row.getSlotIndex());
            vo.setDutyName(row.getDutyName());
            vo.setRemark(row.getRemark());
            return vo;
        }).toList();
    }

    /** SC03 改时段后的目标格校验：8007 → 8006（排除自身）→ 8005（排除自身）→ 8008（无 force，冲突即拒）。 */
    private void validateTargetSlot(Schedule schedule, int weekday, int slotIndex) {
        Map<String, ScheduleRule> ruleMap = ruleService.effectiveRuleMap(schedule.getSubOrgId(), schedule.getSemesterId());
        ScheduleRule rule = ruleMap.get(ruleKey(weekday, slotIndex));
        if (rule == null || rule.getEnabled() == null || rule.getEnabled() != 1) {
            throw new BizException(ScheduleErrorCode.SLOT_NOT_ENABLED, "该时段未启用排班");
        }
        List<Schedule> weekRows = loadWeek(schedule.getSemesterId(), schedule.getWeekNo(), schedule.getSubOrgId());
        long sameSlot = weekRows.stream()
                .filter(s -> s.getWeekday() == weekday && s.getSlotIndex() == slotIndex && !s.getId().equals(schedule.getId()))
                .count();
        if (sameSlot >= rule.getMaxPerson()) {
            throw new BizException(ScheduleErrorCode.SLOT_FULL, "该时段排班人数已达上限（" + rule.getMaxPerson() + " 人）");
        }
        boolean memberScheduled = weekRows.stream()
                .anyMatch(s -> s.getMemberId().equals(schedule.getMemberId()) && !s.getId().equals(schedule.getId()));
        if (memberScheduled) {
            throw new BizException(ScheduleErrorCode.DUPLICATE_SCHEDULE_IN_WEEK, "该人员本周在该子组织已有排班");
        }
        ScheduleMemberSupport.MemberBasic member = memberSupport.requireMember(schedule.getMemberId());
        String classKey = NameNormalizer.classKey(member.className());
        String conflictNote = member.className() == null ? null
                : calcSupport.conflictNote(schedule.getSemesterId(), schedule.getWeekNo(), classKey, weekday, slotIndex);
        if (conflictNote != null) {
            throw new BizException(ScheduleErrorCode.COURSE_CONFLICT, conflictNote);
        }
        schedule.setCourseConflict(0).setConflictNote(null);
    }

    /** 部门管理员的子树可见性：目标子组织不在可见集合 → 1003（超管放行）。 */
    private void checkSubOrgVisible(Long subOrgId) {
        CurrentUser user = UserHolder.getUser();
        List<Long> visible = subOrgSupport.visibleOrgIds(user);
        if (visible != null && !visible.contains(subOrgId)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权操作该子组织的排班");
        }
    }

    private List<Schedule> loadWeek(Long semesterId, Integer weekNo, Long subOrgId) {
        return scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId)
                .eq(Schedule::getWeekNo, weekNo)
                .eq(Schedule::getSubOrgId, subOrgId)
                .eq(Schedule::getStatus, "PLANNED"));
    }

    private Schedule requireOwnedSchedule(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "排班记录不存在");
        }
        checkSubOrgVisible(schedule.getSubOrgId());
        return schedule;
    }

    private ScheduleEntryVO toEntry(Schedule schedule) {
        ScheduleEntryVO vo = new ScheduleEntryVO();
        vo.setId(schedule.getId());
        vo.setMemberId(schedule.getMemberId());
        vo.setMemberName(schedule.getMemberName());
        vo.setClassName(schedule.getMemberClassName());
        vo.setDutyName(schedule.getDutyName());
        vo.setCourseConflict(schedule.getCourseConflict() != null && schedule.getCourseConflict() == 1);
        vo.setConflictNote(schedule.getConflictNote());
        vo.setRemark(schedule.getRemark());
        return vo;
    }

    private String cellKey(int weekday, int slotIndex) {
        return weekday + "-" + slotIndex;
    }

    private String ruleKey(int weekday, int slotIndex) {
        return weekday + "-" + slotIndex;
    }
}
