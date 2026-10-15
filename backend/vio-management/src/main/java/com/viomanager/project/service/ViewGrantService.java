package com.viomanager.project.service;

import com.viomanager.common.result.Result;
import com.viomanager.project.dto.ViewRequestCreateDTO;
import com.viomanager.project.vo.ViewGrantVO;
import java.util.List;

/**
 * 查看申请与授权（PR29–PR31）：走审批中心 PROJECT_VIEW。
 * 申请落 project_view_grant（PENDING），审批通过转 ACTIVE（LIMITED 的 expire_at 生效）；
 * ONCE 授权在 PR03 详情消耗（用完置 EXPIRED）。
 */
public interface ViewGrantService {

    /** PR29 提交查看申请：已有 PENDING 拒（7011）；落 PENDING 并开 PROJECT_VIEW 审批单 */
    Result<Long> createRequest(Long projectId, ViewRequestCreateDTO dto);

    /** PR30 授权列表：仅所有人与管理员 */
    Result<List<ViewGrantVO>> listGrants(Long projectId);

    /** PR31 撤销授权（置 REVOKED）：仅所有人与管理员；PENDING/ACTIVE 可撤，终态 1006 */
    Result<Void> revoke(Long grantId);
}
