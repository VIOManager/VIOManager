package com.viomanager.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 排班 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围说明（照 TeamMapper 先例标 ALL 放行拦截器）：排班可见性不能只靠 org_id——
 * 学生仅看自己的排班（member_id 命中），故由 ScheduleService 按角色显式处理：
 * 超管全部 / 部门管理员本组织子树（org_id = sub_org_id）/ 学生仅本人。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface ScheduleMapper extends BaseMapper<Schedule> {
}
