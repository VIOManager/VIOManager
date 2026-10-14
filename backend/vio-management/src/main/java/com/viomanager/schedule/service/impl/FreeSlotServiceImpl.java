package com.viomanager.schedule.service.impl;

import com.viomanager.common.enums.ScheduleErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.schedule.service.FreeSlotService;
import com.viomanager.schedule.support.FreeSlotCalcSupport;
import com.viomanager.schedule.support.SemesterSupport;
import com.viomanager.schedule.vo.FreeMemberVO;
import com.viomanager.schedule.vo.FreeSlotCellVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 空课查询实现：名单只含已确认映射班级的成员（PRD 铁规则），单双周已按周次奇偶计入。
 */
@Service
public class FreeSlotServiceImpl implements FreeSlotService {

    @Resource
    private FreeSlotCalcSupport calcSupport;

    @Resource
    private SemesterSupport semesterSupport;

    @Override
    public List<FreeSlotCellVO> matrix(Long semesterId, Integer weekNo) {
        List<FreeSlotCalcSupport.FreeMemberRow> candidates = loadCandidates(semesterId, weekNo);
        Set<FreeSlotCalcSupport.OccupyKey> occupied = calcSupport.occupiedCells(semesterId, weekNo);
        List<FreeSlotCellVO> result = new ArrayList<>(35);
        for (int weekday = 1; weekday <= 7; weekday++) {
            for (int slotIndex = 1; slotIndex <= 5; slotIndex++) {
                final int wd = weekday;
                final int si = slotIndex;
                Map<String, Integer> gradeDist = new LinkedHashMap<>();
                candidates.stream()
                        .filter(m -> calcSupport.isFree(occupied, m.classKey(), wd, si))
                        .forEach(m -> gradeDist.merge(m.grade() == null ? "未知" : m.grade(), 1, Integer::sum));
                FreeSlotCellVO cell = new FreeSlotCellVO();
                cell.setWeekday(weekday);
                cell.setSlotIndex(slotIndex);
                cell.setFreeCount(gradeDist.values().stream().mapToInt(Integer::intValue).sum());
                cell.setGradeDist(gradeDist);
                result.add(cell);
            }
        }
        return result;
    }

    @Override
    public List<FreeMemberVO> members(Long semesterId, Integer weekNo, Integer weekday, Integer slotIndex) {
        if (weekday == null || weekday < 1 || weekday > 7) {
            throw new BizException(ScheduleErrorCode.WEEK_OUT_OF_RANGE, "weekday 须在 1–7 内");
        }
        if (slotIndex == null || slotIndex < 1 || slotIndex > 5) {
            throw new BizException(ScheduleErrorCode.WEEK_OUT_OF_RANGE, "slotIndex 须在 1–5 内");
        }
        List<FreeSlotCalcSupport.FreeMemberRow> candidates = loadCandidates(semesterId, weekNo);
        Set<FreeSlotCalcSupport.OccupyKey> occupied = calcSupport.occupiedCells(semesterId, weekNo);
        return candidates.stream()
                .filter(m -> calcSupport.isFree(occupied, m.classKey(), weekday, slotIndex))
                .map(m -> {
                    FreeMemberVO vo = new FreeMemberVO();
                    vo.setMemberId(m.memberId());
                    vo.setName(m.name());
                    vo.setClassName(m.className());
                    vo.setGrade(m.grade());
                    return vo;
                })
                .toList();
    }

    /** 教学周范围校验（8010）+ 拉取空课候选名单（已确认班级的在册成员，年级已推导）。 */
    private List<FreeSlotCalcSupport.FreeMemberRow> loadCandidates(Long semesterId, Integer weekNo) {
        semesterSupport.checkWeekInRange(semesterId, weekNo, ScheduleErrorCode.WEEK_OUT_OF_RANGE);
        return calcSupport.freeMemberCandidates(semesterId);
    }
}
