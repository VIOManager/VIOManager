package com.viomanager.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.approval.entity.Approval;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批单 Mapper。
 * ⚠️ SELF 声明的理由同 SysUserMapper：审批的三视角过滤（待办按审批组织子树、
 * 我发起的 / 我是目标人按 member_id）语义特殊，由 service 层自管，
 * 不吃数据范围拦截器的通用注入与学生默认拒绝。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface ApprovalMapper extends BaseMapper<Approval> {
}
