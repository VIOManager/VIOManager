package com.viomanager.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.performance.entity.PerformanceWeekly;
import org.apache.ibatis.annotations.Mapper;

/**
 * 绩效周报 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围说明（照 TeamMapper 先例标 ALL 放行拦截器）：周报可见性不能只靠 org_id——
 * 学生仅看/操作自己的周报（member_id 命中，PF01–PF05），故由 PerformanceWeeklyService 按角色显式处理：
 * 超管全部 / 部门管理员本组织子树（org_id = sub_org_id）/ 学生仅本人。
 * UNIQUE KEY (member_id, semester_id, week_no, sub_org_id, deleted) 兜底 8041。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface PerformanceWeeklyMapper extends BaseMapper<PerformanceWeekly> {
}
