package com.viomanager.project.service;

import com.viomanager.common.result.Result;
import com.viomanager.project.dto.MilestoneCreateDTO;
import com.viomanager.project.dto.MilestoneUpdateDTO;
import com.viomanager.project.vo.MilestoneVO;
import java.util.List;

/**
 * 比赛时间节点（PR16–PR20）：部门管理员维护的"闹钟"，审核通过后自动同步任务。
 * 写口仅部门管理员（本子树比赛）与超管；学生只读 APPROVED。
 */
public interface MilestoneService {

    /** PR16 节点列表：管理员看全部（部门管理员限本子树比赛），普通用户仅 APPROVED */
    Result<List<MilestoneVO>> list(Long contestId, String status, String nodeType);

    /** PR17 创建节点（DRAFT）：仅部门管理员与超管 */
    Result<MilestoneVO> create(Long contestId, MilestoneCreateDTO dto);

    /** PR18 编辑节点：仅 DRAFT 与 REJECTED 可编辑（7009） */
    Result<Void> update(Long nodeId, MilestoneUpdateDTO dto);

    /** PR19 删除节点：仅 DRAFT（7009） */
    Result<Void> delete(Long nodeId);

    /** PR20 提交审核：转 PENDING 并开 MILESTONE_APPROVE 审批单 */
    Result<Void> submit(Long nodeId);
}
