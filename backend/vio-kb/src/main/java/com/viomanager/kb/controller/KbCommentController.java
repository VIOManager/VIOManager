package com.viomanager.kb.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.kb.dto.KbCommentCreateDTO;
import com.viomanager.kb.dto.KbCommentHideDTO;
import com.viomanager.kb.service.KbCommentService;
import com.viomanager.kb.vo.KbCommentVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库评论接口（KB08-11）。评论即时显示不审核（拍板口径），违规靠管理员隐藏/删除。
 * KB10 的"本人或管理员"在 service 判定（同一端点两种身份，不适合角色注解一刀切）。
 */
@RestController
@RequestMapping("/api/kb")
public class KbCommentController {

    @Resource
    private KbCommentService kbCommentService;

    /** KB08 评论列表：顶层分页时间正序，含楼中楼（parent_id 折叠） */
    @GetMapping("/docs/{id}/comments")
    public Result<PageResult<KbCommentVO>> list(
            @PathVariable("id") Long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return kbCommentService.list(id, page, size);
    }

    /** KB09 发表/回复评论（幂等键） */
    @Idempotent(bizType = "KB_COMMENT_CREATE")
    @PostMapping("/docs/{id}/comments")
    public Result<Long> create(@PathVariable("id") Long id,
                               @Valid @RequestBody KbCommentCreateDTO dto) {
        return kbCommentService.create(id, dto);
    }

    /** KB10 删除评论：本人或管理员（service 校验） */
    @DeleteMapping("/comments/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return kbCommentService.delete(id);
    }

    /** KB11 隐藏/恢复评论（管理员）：计数随可见口径 ±1 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "KB", label = "隐藏/恢复评论",
            summary = "'评论显隐：' + #id + ' hidden=' + #dto.hidden")
    @PutMapping("/comments/{id}/hide")
    public Result<Void> hide(@PathVariable("id") Long id,
                             @Valid @RequestBody KbCommentHideDTO dto) {
        return kbCommentService.hide(id, dto.getHidden());
    }
}
