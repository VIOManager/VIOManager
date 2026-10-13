package com.viomanager.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.contest.entity.ContestSource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部比赛待处理池 mapper（单表 CRUD 走 BaseMapper）。
 * 仅管理端可见，标 ALL 放行拦截器；Controller 用 @RequireRole 挡学生。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface ContestSourceMapper extends BaseMapper<ContestSource> {
}
