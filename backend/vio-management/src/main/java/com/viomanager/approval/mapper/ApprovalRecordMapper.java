package com.viomanager.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.approval.entity.ApprovalRecord;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批流水 Mapper。SELF 声明：流水只随详情按 approval_id 查，权限由详情接口把关。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {
}
