package com.viomanager.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.team.entity.TeamAdvisor;
import org.apache.ibatis.annotations.Mapper;

/**
 * 赛队指导教师 mapper（单表 CRUD 走 BaseMapper）。
 * 标 ALL：查询按 team_id 过滤，可见性由 service 层保证。关联 member 展示走 JdbcTemplate。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface TeamAdvisorMapper extends BaseMapper<TeamAdvisor> {
}
