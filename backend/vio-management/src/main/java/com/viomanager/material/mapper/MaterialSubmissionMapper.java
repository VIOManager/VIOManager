package com.viomanager.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.material.entity.MaterialSubmission;
import java.util.List;
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
     * 🔴 并发两道防线（本表无 (team_id, requirement_id, is_current) 唯一约束）：
     *    第一道 {@link #lockByRequirementAndTeam} 的 FOR UPDATE 锁（先拿锁，含首次提交 0 行场景），
     *    第二道本条件更新（只动 is_current=1 的行）；插入新版本必须在这两步之后，且全程同一事务。
     * 注意：@TableLogic 只对 BaseMapper 自动方法与 Wrapper 生效，**手写 SQL 必须自己带 deleted = 0**。
     */
    @Update("UPDATE material_submission SET is_current = 0, updated_at = NOW() "
            + "WHERE requirement_id = #{requirementId} AND team_id = #{teamId} "
            + "AND is_current = 1 AND deleted = 0")
    int invalidateCurrent(@Param("requirementId") Long requirementId, @Param("teamId") Long teamId);

    /**
     * 并发防线第一道：对该队该要求的提交行加 FOR UPDATE 锁。
     * 已有当前行时锁住它们；0 行（首次提交）时 MySQL 以间隙 / next-key 锁封住同键插入——
     * 本表无唯一索引可兜底，无索引列上的 FOR UPDATE 会锁住扫描范围，同样能把同键并发插入串行化。
     * submit / MS03 批量 / A06 覆盖式 upsert 都必须**先**拿这把锁，再做「失效旧版 + 查最大版本 + 插新行」。
     * ⚠️ 刻意不带 deleted = 0：纯锁原语（返回值调用方丢弃），不过滤已删行锁得更全，
     * 且在 READ COMMITTED 下已删行上的记录锁同样能串行化插入。
     */
    @Select("SELECT id FROM material_submission WHERE requirement_id = #{requirementId} "
            + "AND team_id = #{teamId} FOR UPDATE")
    List<Long> lockByRequirementAndTeam(@Param("requirementId") Long requirementId, @Param("teamId") Long teamId);

    /**
     * 查该队某要求下的最大版本号（无记录返回 null，调用方按 1 起算）。
     * 手写 SQL 必须显式带 deleted = 0（含历史失效版本，故不加 is_current 条件）。
     */
    @Select("SELECT MAX(version_no) FROM material_submission "
            + "WHERE requirement_id = #{requirementId} AND team_id = #{teamId} AND deleted = 0")
    Integer selectMaxVersionNo(@Param("requirementId") Long requirementId, @Param("teamId") Long teamId);

    /**
     * 查该队当前生效提交覆盖的要求 ID 集合（齐全率重算用，DISTINCT 去重）。
     * 手写 SQL 显式带 deleted = 0（@TableLogic 只对 BaseMapper 自动方法生效）。
     */
    @Select("SELECT DISTINCT requirement_id FROM material_submission "
            + "WHERE team_id = #{teamId} AND is_current = 1 AND deleted = 0")
    List<Long> selectCurrentRequirementIds(@Param("teamId") Long teamId);
}
