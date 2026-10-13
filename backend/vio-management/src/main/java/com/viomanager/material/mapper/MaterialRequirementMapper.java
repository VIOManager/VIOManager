package com.viomanager.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.material.entity.MaterialRequirement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 材料要求 Mapper。
 *
 * ⚠️ 本表**无 org_id 列**（归属组织需 join material_task），因此不能用 ORG 数据范围自动注入，
 * 声明 SELF 避免拦截器误判 + 学生 403。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface MaterialRequirementMapper extends BaseMapper<MaterialRequirement> {
}
