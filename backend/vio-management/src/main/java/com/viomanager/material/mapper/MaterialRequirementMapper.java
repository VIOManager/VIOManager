package com.viomanager.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.material.entity.MaterialRequirement;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 材料要求 Mapper。
 *
 * ⚠️ 本表**无 org_id 列**（归属组织需 join material_task），因此不能用 ORG 数据范围自动注入，
 * 声明 SELF 避免拦截器误判 + 学生 403。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface MaterialRequirementMapper extends BaseMapper<MaterialRequirement> {

    /**
     * §5 联动专用：原子插入系统佐证要求（material_type=EVIDENCE、required_flag=1、max_size_mb=20、
     * system_generated=1、sort_no=999——系统追加项管理端不可增删，MR02/MR03 拦截的就是这个标记）。
     * INSERT ... SELECT ... WHERE NOT EXISTS：同任务已有未删 EVIDENCE 要求时返回 0（调用方据此跳过），
     * 并发下也只可能落一条——封住原先 selectCount + insert 的检查-插入竞态窗口。
     * id 由调用方用 IdWorker.getId() 生成传入（手写 SQL 不走 MyBatis-Plus 主键自动填充）。
     * ⚠️ @TableLogic 只对 BaseMapper 自动方法生效，本 SQL 的 NOT EXISTS 显式带 deleted = 0；
     * created_by / updated_by 列可空，联动场景无登录人上下文，留空。
     */
    @Insert("INSERT INTO material_requirement (id, task_id, contest_id, material_type, name, required_flag, "
            + "allowed_formats, max_size_mb, naming_rule, system_generated, sort_no, deleted, "
            + "created_at, updated_at) "
            + "SELECT #{id}, #{taskId}, #{contestId}, 'EVIDENCE', #{name}, 1, #{formats}, 20, #{namingRule}, 1, 999, 0, "
            + "NOW(), NOW() FROM DUAL WHERE NOT EXISTS ("
            + "SELECT 1 FROM material_requirement WHERE task_id = #{taskId} "
            + "AND material_type = 'EVIDENCE' AND deleted = 0)")
    int insertEvidenceIfAbsent(@Param("id") Long id, @Param("taskId") Long taskId,
                               @Param("contestId") Long contestId, @Param("name") String name,
                               @Param("formats") String formats, @Param("namingRule") String namingRule);
}
