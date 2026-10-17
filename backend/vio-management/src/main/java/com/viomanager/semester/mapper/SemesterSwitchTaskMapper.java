package com.viomanager.semester.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.semester.entity.SemesterSwitchTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学期切换任务 mapper（单表 CRUD 走 BaseMapper；占位/收尾的状态条件更新在 service 层手写）。
 * 数据范围 SELF：任务属全平台维度，且接口仅超管可进，无需拦截器注入。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface SemesterSwitchTaskMapper extends BaseMapper<SemesterSwitchTask> {
}
