package com.viomanager.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.material.entity.MaterialSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 材料提交 Mapper。
 *
 * ⚠️ 本表有 org_id（赛队所属组织），是全组数据范围过滤的强依据列之一；
 * 但管理端概览已由 service 按 teamId 精确取数，故仍声明 SELF 自管，理由同 ApprovalMapper。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface MaterialSubmissionMapper extends BaseMapper<MaterialSubmission> {

    /**
     * 覆盖上传：把该队该要求下所有生效版本置为失效，返回影响行数。
     * 🔴 本表无 (team_id, requirement_id, is_current) 唯一约束，这是封住并发的关键一步：
     *    必须先执行本更新再插入新版本，且整个动作在同一事务内。
     * 注意：@TableLogic 只对 BaseMapper 自动方法与 Wrapper 生效，**手写 SQL 必须自己带 deleted = 0**。
     */
    @Update("UPDATE material_submission SET is_current = 0, updated_at = NOW() "
            + "WHERE requirement_id = #{requirementId} AND team_id = #{teamId} "
            + "AND is_current = 1 AND deleted = 0")
    int invalidateCurrent(@Param("requirementId") Long requirementId, @Param("teamId") Long teamId);

    /**
     * 查该队某要求下的最大版本号（无记录返回 null，调用方按 1 起算）。
     * 手写 SQL 必须显式带 deleted = 0（含历史失效版本，故不加 is_current 条件）。
     */
    @Select("SELECT MAX(version_no) FROM material_submission "
            + "WHERE requirement_id = #{requirementId} AND team_id = #{teamId} AND deleted = 0")
    Integer selectMaxVersionNo(@Param("requirementId") Long requirementId, @Param("teamId") Long teamId);
}
