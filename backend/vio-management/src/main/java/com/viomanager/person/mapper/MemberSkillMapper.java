package com.viomanager.person.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.person.entity.MemberSkill;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 人员技能标签 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围 ALL：member_skill 表没有组织列可注入过滤，行的可见范围完全跟随
 * 其所属 person（由 PersonMapper 的 ORG 范围与 controller 的角色注解控制），
 * 这里若标 ORG 会被拦截器注入不存在的 org_id 列导致 SQL 报错。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface MemberSkillMapper extends BaseMapper<MemberSkill> {

    /**
     * 按人员物理删除技能标签（绕过 @TableLogic 的逻辑删除）。
     * ⚠️ 存在原因：member_skill 有唯一键 uk_member_skill(member_id, skill_value, deleted)，
     * 若"整组替换"走逻辑删除，同一技能"删除→再加回→再删除"第二轮回填 deleted=1 时
     * 会与旧行撞唯一键报 1062；标签是档案的从属数据，替换场景物理删除即可。
     */
    @Delete("DELETE FROM member_skill WHERE member_id = #{memberId}")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
