package com.viomanager.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.performance.entity.PerformanceScore;
import org.apache.ibatis.annotations.Mapper;

/**
 * 绩效维度得分 mapper。
 * ⚠️ 本表无逻辑删除列（审计仅 created_at/updated_at），BaseMapper 的 delete 系列不可用——
 * 重打分场景按 weekly_id 全量物理 delete 后重插（打分本身仅 SUBMITTED 状态允许，幂等）。
 * 无独立查询接口，仅 WeeklyService 聚合内部使用。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface PerformanceScoreMapper extends BaseMapper<PerformanceScore> {
}
