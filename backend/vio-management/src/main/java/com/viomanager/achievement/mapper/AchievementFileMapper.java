package com.viomanager.achievement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.achievement.entity.AchievementFile;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 成果附件 Mapper。
 *
 * ⚠️ 本表**无 org_id 列**：数据范围过滤必须 join achievement，故声明 SELF 自管。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface AchievementFileMapper extends BaseMapper<AchievementFile> {

    /**
     * 佐证覆盖上传（A06，单文件覆盖式）：把该成果下当前生效的同类附件置为失效。
     * 与 MaterialSubmissionMapper.invalidateCurrent 同理，手写 SQL 必须自带 deleted = 0。
     */
    @Update("UPDATE achievement_file SET is_current = 0, updated_at = NOW() "
            + "WHERE achievement_id = #{achievementId} AND file_type = #{fileType} "
            + "AND is_current = 1 AND deleted = 0")
    int invalidateCurrent(@Param("achievementId") Long achievementId, @Param("fileType") String fileType);

    /**
     * 查该成果某类型附件的最大版本号（无记录返回 null，调用方按 1 起算）。
     * 手写 SQL 显式带 deleted = 0（含历史失效版本，故不加 is_current 条件）。
     */
    @Select("SELECT MAX(version_no) FROM achievement_file "
            + "WHERE achievement_id = #{achievementId} AND file_type = #{fileType} AND deleted = 0")
    Integer selectMaxVersionNo(@Param("achievementId") Long achievementId, @Param("fileType") String fileType);
}
