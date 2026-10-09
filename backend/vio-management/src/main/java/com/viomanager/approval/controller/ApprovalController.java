package com.viomanager.approval.controller;

import com.viomanager.approval.dto.ApprovalActionDTO;
import com.viomanager.approval.dto.ApprovalQueryDTO;
import com.viomanager.approval.dto.ApprovalReadDTO;
import com.viomanager.approval.service.IApprovalActionService;
import com.viomanager.approval.service.IApprovalQueryService;
import com.viomanager.approval.vo.ApprovalDetailVO;
import com.viomanager.approval.vo.ApprovalVO;
import com.viomanager.approval.vo.UnreadCountVO;
import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批中心接口（P01–P09）。口径以骨架接口清单 §6 为准。
 * P02/P03 是管理员动作（@RequireRole 挡学生）；P04–P06 是发起人 / 目标人动作，
 * 权限在 service 按人员档案 ID 判断（学生也能操作自己的单子）。
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    @Resource
    private IApprovalQueryService queryService;

    @Resource
    private IApprovalActionService actionService;

    /** P01 三视角列表：view status bizType page size，分页格式按规范 §2.3 */
    @GetMapping
    public Result<PageResult<ApprovalVO>> list(@Valid ApprovalQueryDTO dto) {
        return queryService.list(dto);
    }

    /** P02 管理员批准：事务内按 bizType 路由回调，按 biz_id 幂等 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "APPROVAL_APPROVE")
    @AuditLog(bizType = "APPROVAL_APPROVE", label = "批准审批", summary = "'批准审批单：' + #id")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable("id") Long id,
                                @Valid @RequestBody ApprovalActionDTO dto) {
        actionService.approve(id, dto);
        return Result.ok();
    }

    /** P03 管理员驳回 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "APPROVAL_REJECT")
    @AuditLog(bizType = "APPROVAL_REJECT", label = "驳回审批", summary = "'驳回审批单：' + #id")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable("id") Long id,
                               @Valid @RequestBody ApprovalActionDTO dto) {
        actionService.reject(id, dto);
        return Result.ok();
    }

    /** P04 发起人取消：仅 PENDING 状态可取消 */
    @Idempotent(bizType = "APPROVAL_CANCEL")
    @AuditLog(bizType = "APPROVAL_CANCEL", label = "取消申请", summary = "'取消审批单：' + #id")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id,
                               @Valid @RequestBody ApprovalActionDTO dto) {
        actionService.cancel(id, dto);
        return Result.ok();
    }

    /** P05 目标人同意（如被邀请队员） */
    @Idempotent(bizType = "APPROVAL_AGREE")
    @AuditLog(bizType = "APPROVAL_AGREE", label = "同意邀请", summary = "'同意审批单：' + #id")
    @PostMapping("/{id}/agree")
    public Result<Void> agree(@PathVariable("id") Long id,
                              @Valid @RequestBody ApprovalActionDTO dto) {
        actionService.agree(id, dto);
        return Result.ok();
    }

    /** P06 目标人拒绝 */
    @Idempotent(bizType = "APPROVAL_REFUSE")
    @AuditLog(bizType = "APPROVAL_REFUSE", label = "拒绝邀请", summary = "'拒绝审批单：' + #id")
    @PostMapping("/{id}/refuse")
    public Result<Void> refuse(@PathVariable("id") Long id,
                               @Valid @RequestBody ApprovalActionDTO dto) {
        actionService.refuse(id, dto);
        return Result.ok();
    }

    /** P07 已读标记：按 asRole 只置当前视角的已读位 */
    @PutMapping("/read")
    public Result<Void> markRead(@Valid @RequestBody ApprovalReadDTO dto) {
        return queryService.markRead(dto);
    }

    /** P08 两类未读数：todoCount 待处理邀请 + resultCount 我的申请已出结果 */
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return queryService.unreadCount();
    }

    /** P09 详情：含申请内容快照与操作流水 */
    @GetMapping("/{id}")
    public Result<ApprovalDetailVO> detail(@PathVariable("id") Long id) {
        return queryService.detail(id);
    }
}
