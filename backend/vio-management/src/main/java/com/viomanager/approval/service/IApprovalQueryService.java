package com.viomanager.approval.service;

import com.viomanager.approval.dto.ApprovalQueryDTO;
import com.viomanager.approval.dto.ApprovalReadDTO;
import com.viomanager.approval.vo.ApprovalDetailVO;
import com.viomanager.approval.vo.ApprovalVO;
import com.viomanager.approval.vo.UnreadCountVO;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;

/**
 * 审批查询服务：三视角列表、详情、已读标记、未读数（P01/P07/P08/P09）。
 */
public interface IApprovalQueryService {

    /** 三视角列表：TODO 管理员待办 / MINE 我发起的 / TARGET 我是目标人（P01） */
    Result<PageResult<ApprovalVO>> list(ApprovalQueryDTO dto);

    /** 详情：含申请内容快照与操作流水（P09） */
    Result<ApprovalDetailVO> detail(Long id);

    /** 已读标记：按 asRole 只置当前视角的已读位（P07） */
    Result<Void> markRead(ApprovalReadDTO dto);

    /** 两类未读数：todoCount 待处理邀请 + resultCount 我的申请已出结果（P08） */
    Result<UnreadCountVO> unreadCount();
}
