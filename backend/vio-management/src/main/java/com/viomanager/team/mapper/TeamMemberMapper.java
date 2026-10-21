package com.viomanager.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.team.entity.TeamMember;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 赛队成员 mapper（单表 CRUD 走 BaseMapper）。
 * 标 ALL：成员查询一律按 team_id 显式过滤，service 层先做赛队可见性校验（TE02/11 非 4008）。
 * 关联 member 的展示查询走 JdbcTemplate 显式列名。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface TeamMemberMapper extends BaseMapper<TeamMember> {

    /**
     * 重入队 upsert：uk_team_member(team_id, member_id, deleted) 含 deleted 不含 status，
     * 退出/被移除/拒绝后的旧行（deleted=0）会让纯 INSERT 撞唯一键（2026-10-16 全量冒烟抓出）。
     * 调用方前置校验已排除 UNCONFIRMED/CONFIRMED 活跃行，撞键时旧行必为终态，复位为 UNCONFIRMED 安全。
     * ⚠️ 自定义 SQL 不走 MP ASSIGN_ID，调用方必须 setId(IdWorker.getId())（同 M9 论坛 upsert 先例）。
     */
    @Insert("INSERT INTO team_member (id, team_id, member_id, member_name, member_org_id, is_leader, status) "
            + "VALUES (#{id}, #{teamId}, #{memberId}, #{memberName}, #{memberOrgId}, 0, 'UNCONFIRMED') "
            + "ON DUPLICATE KEY UPDATE status = 'UNCONFIRMED', member_name = VALUES(member_name), "
            + "member_org_id = VALUES(member_org_id), is_leader = 0, "
            + "joined_at = NULL, left_at = NULL, left_reason = NULL")
    int upsertRejoin(TeamMember m);
}
