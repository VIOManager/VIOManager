package com.viomanager.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.contest.entity.ContestTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 比赛模板 mapper（单表 CRUD 走 BaseMapper）。
 * 模板为全平台公共数据，各管理端可见，标 ALL 放行数据范围拦截器。
 * 列表带 refCount（被比赛引用计数）走 JdbcTemplate 显式列名 + COUNT 子查询。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface ContestTemplateMapper extends BaseMapper<ContestTemplate> {
}
