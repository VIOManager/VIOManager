package com.viomanager.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.team.entity.TeamMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 赛队成员 mapper（单表 CRUD 走 BaseMapper）。
 * 标 ALL：成员查询一律按 team_id 显式过滤，service 层先做赛队可见性校验（TE02/11 非 4008）。
 * 关联 member 的展示查询走 JdbcTemplate 显式列名。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface TeamMemberMapper extends BaseMapper<TeamMember> {
}
