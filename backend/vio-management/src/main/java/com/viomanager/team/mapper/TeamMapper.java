package com.viomanager.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.team.entity.Team;
import org.apache.ibatis.annotations.Mapper;

/**
 * 赛队 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围说明：赛队可见性不能只靠 org_id 列——学生仅看自己所在队伍（须 JOIN team_member），
 * 故 mapper 标 ALL 放行拦截器，可见性过滤由 TeamQueryService 按角色显式处理：
 *  超管全部 / 部门管理员本组织子树 / 学生仅 member_id 命中的队伍。
 * 多表 JOIN（列表带比赛名/队长名/计数、成员搜索）走 JdbcTemplate 显式列名，禁 SELECT *。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface TeamMapper extends BaseMapper<Team> {
}
