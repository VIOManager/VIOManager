package com.viomanager.project.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.project.dto.ViewRequestCreateDTO;
import com.viomanager.project.service.ViewGrantService;
import com.viomanager.project.vo.ViewGrantVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查看申请与授权接口（PR29–PR31），口径以 docs/api/项目管理-接口清单.md §3.7 为准。
 * PR29 登录即可（申请人=当前用户）；PR30/PR31 仅所有人与管理员（service 判定）。
 * @Idempotent 暂不挂（同全站口径：前端未带 X-Idempotency-Key 头）。
 */
@RestController
public class ViewGrantController {

    @Resource
    private ViewGrantService viewGrantService;

    /** PR29 提交查看申请：ONCE/LIMITED；已有 PENDING 拒（7011） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_VIEW_REQUEST", label = "提交查看申请",
            summary = "'项目' + #id + ' 提交查看申请'")
    @PostMapping("/api/projects/{id}/view-requests")
    public Result<Long> createRequest(@PathVariable("id") Long id,
                                      @Valid @RequestBody ViewRequestCreateDTO dto) {
        return viewGrantService.createRequest(id, dto);
    }

    /** PR30 授权列表：谁申请过、类型、状态、到期时间；仅所有人与管理员 */
    @GetMapping("/api/projects/{id}/view-grants")
    public Result<List<ViewGrantVO>> listGrants(@PathVariable("id") Long id) {
        return viewGrantService.listGrants(id);
    }

    /** PR31 撤销授权（置 REVOKED）：仅所有人与管理员 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_VIEW_REVOKE", label = "撤销查看授权",
            summary = "'撤销查看授权 ' + #id")
    @PutMapping("/api/view-grants/{id}/revoke")
    public Result<Void> revoke(@PathVariable("id") Long id) {
        return viewGrantService.revoke(id);
    }
}
