package com.viomanager.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.performance.entity.PerformanceRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 绩效规则 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围说明：规则仅管理端配置（PF11/PF12），可见性由 PerformanceRuleService 显式控制。
 * MySQL 唯一索引对 NULL 不去重：默认规则（semester_id 为空）的同键重复由 service 先查后插兜底。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface PerformanceRuleMapper extends BaseMapper<PerformanceRule> {
}
