package com.viomanager.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.contest.entity.Contest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 比赛 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围不能只靠 org_id：学生仅可见已发布及之后状态的比赛，
 * 故 mapper 标 ALL 放行拦截器，可见性过滤由 ContestQueryService 按角色显式处理：
 *  超管全部 / 部门管理员本组织子树 / 学生仅 PUBLISHED 及之后。
 * 列表带学期名、赛道数、赛队数走 JdbcTemplate 显式列名，禁 SELECT *。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface ContestMapper extends BaseMapper<Contest> {
}
