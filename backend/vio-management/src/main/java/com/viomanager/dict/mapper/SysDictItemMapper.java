package com.viomanager.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.dict.entity.SysDictItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 字典项 Mapper。
 * ⚠️ selectEnabledByCode 必须声明 @DataScope(ALL)：字典是全平台共享数据，
 * 学生查询未声明范围的 mapper 会被数据范围拦截器默认拒绝（Step 4 的防泄露底线），
 * 字典下拉人人要用，声明 ALL 放行不动 SQL。
 * 手写 SQL 显式带 deleted = 0（@TableLogic 对手写 SQL 不生效，见 BaseEntity 说明）。
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    /**
     * 按字典编码查启用中的字典项，排序号小的在前。
     * PRESET 与 USER 来源都返回、审核状态不筛——auditStatus 带回给前端自行处理
     * （A 线的审核页面与本接口共用一份数据口径）。
     */
    @DataScope(scope = DataScope.Scope.ALL)
    @Select("SELECT id, dict_code, item_value, item_label, source, audit_status, sort_no, status, deleted, "
            + "created_at, created_by, updated_at, updated_by "
            + "FROM sys_dict_item "
            + "WHERE dict_code = #{dictCode} AND status = 1 AND deleted = 0 "
            + "ORDER BY sort_no, id")
    List<SysDictItem> selectEnabledByCode(@Param("dictCode") String dictCode);
}
