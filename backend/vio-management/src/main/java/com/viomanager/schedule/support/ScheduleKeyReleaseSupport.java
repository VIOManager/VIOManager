package com.viomanager.schedule.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.viomanager.schedule.entity.Schedule;
import com.viomanager.schedule.mapper.ScheduleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 排班唯一键释放。uk_schedule_conflict（semester+week+subOrg+member+slot+deleted）
 * 尾列是 deleted，而 cancel 只置 status=CANCELLED 不动 deleted——已取消的行永久占键，
 * 重排/改格到同键时唯一键冲突炸 500。拍板「同周同人同子组织仅一条有效排班」：
 * 已取消的行被新排班取代时逻辑删除；走 delete(wrapper) 因 @TableLogic 字段
 * 无法经 updateById 更新。
 */
@Component
public class ScheduleKeyReleaseSupport {

    @Resource
    private ScheduleMapper scheduleMapper;

    /** 释放 (semester, week, subOrg, member, slot) 键上已取消（CANCELLED）的占键行 */
    public void releaseSuperseded(Long semesterId, Integer weekNo, Long subOrgId, Long memberId, int slotIndex) {
        scheduleMapper.delete(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, semesterId)
                .eq(Schedule::getWeekNo, weekNo)
                .eq(Schedule::getSubOrgId, subOrgId)
                .eq(Schedule::getMemberId, memberId)
                .eq(Schedule::getSlotIndex, slotIndex)
                .eq(Schedule::getStatus, "CANCELLED"));
    }
}
