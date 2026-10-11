package com.viomanager.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.material.entity.MaterialTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 材料任务 Mapper。
 *
 * ⚠️ 数据范围口径（照骨架 ApprovalMapper 的先例声明 SELF）：
 * 骨架拦截器「无声明时学生一律拒绝」，而任务列表管理端全量、学生不直接访问本表；
 * 是否按 org_id 自动注入子树由 service 层显式控制，不吃拦截器的默认行为。
 * 若组长要求管理端任务列表自动按部门子树过滤，改成 @DataScope(scope = ORG)（本表有 org_id 列）。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface MaterialTaskMapper extends BaseMapper<MaterialTask> {
}
