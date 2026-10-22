package com.viomanager.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.performance.entity.PerformanceSummary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 绩效汇总 mapper（单表 CRUD 走 BaseMapper）。
 * 汇总无独立写接口：由 PF07 打分事务内经 PerfCalcSupport 重算落库；
 * 查询侧 PF05（本人）/PF09（管理端按子树）由 PerformanceSummaryService 显式控制可见性。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface PerformanceSummaryMapper extends BaseMapper<PerformanceSummary> {
}
