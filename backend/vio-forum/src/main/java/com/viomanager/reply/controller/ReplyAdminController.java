package com.viomanager.reply.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.reply.dto.ReplyStatusDTO;
import com.viomanager.reply.dto.ReplyUpdateDTO;
import com.viomanager.reply.service.IReplyAdminService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理档评论端点（F13/F14，2026-10-17 拍板：超管与部门管理员同权，论坛只有学生/管理两档）。
 */
@RestController
@RequestMapping("/api/forum/replies")
public class ReplyAdminController {

    @Resource
    private IReplyAdminService replyAdminService;

    /** F13 编辑评论（软编辑：覆盖原文，edited=1 透出）；审计 biz_type 与幂等注解共用同一词表 */
    @AuditLog(bizType = "FORUM_REPLY_ADMIN_UPDATE", label = "管理员编辑楼层", summary = "'编辑楼层 #' + #id")
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "FORUM_REPLY_ADMIN_UPDATE")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody ReplyUpdateDTO dto) {
        return Result.ok(replyAdminService.update(id, dto));
    }

    /** F14 状态设置：删除=HIDDEN（软删占位）/ 恢复=NORMAL */
    @AuditLog(bizType = "FORUM_REPLY_ADMIN_STATUS", label = "楼层状态变更", summary = "'楼层 #' + #id + ' → ' + #dto.to")
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "FORUM_REPLY_ADMIN_STATUS")
    @PostMapping("/{id}/status")
    public Result<Map<String, Object>> setStatus(@PathVariable("id") Long id,
                                                 @Valid @RequestBody ReplyStatusDTO dto) {
        return Result.ok(replyAdminService.setStatus(id, dto));
    }
}
