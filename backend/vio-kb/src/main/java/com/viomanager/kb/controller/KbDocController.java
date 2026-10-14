package com.viomanager.kb.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.kb.dto.KbAdminQueryDTO;
import com.viomanager.kb.dto.KbDocCreateDTO;
import com.viomanager.kb.dto.KbDocQueryDTO;
import com.viomanager.kb.dto.KbDocStatusDTO;
import com.viomanager.kb.dto.KbDocUpdateDTO;
import com.viomanager.kb.service.KbDocService;
import com.viomanager.kb.vo.KbDocDetailVO;
import com.viomanager.kb.vo.KbDocVO;
import com.viomanager.kb.vo.KbPromptVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
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
 * 知识库文档接口（KB01-04 浏览 + KB12-17 管理），路径以《知识库-接口清单》为准。
 * 浏览侧登录即可（PUBLISHED 全员可见拍板口径）；管理侧角色注解收口。
 * ⚠️ 联调项：前端 kb.ts 暂定版路径为 /api/kb/items，需按权威清单调整为 /api/kb/docs
 * （前端对接 README 裁定：以权威清单为准）；写接口前端须补 X-Idempotency-Key 头。
 */
@RestController
@RequestMapping("/api/kb")
public class KbDocController {

    @Resource
    private KbDocService kbDocService;

    /** KB01 浏览列表：筛选 category/keyword/sourceType，仅 PUBLISHED，置顶优先再时间倒序 */
    @GetMapping("/docs")
    public Result<List<KbDocVO>> list(KbDocQueryDTO dto) {
        return kbDocService.list(dto);
    }

    /** KB03 推荐位：置顶+最新 N（默认 5 上限 10），卡片数据源 */
    @GetMapping("/docs/recommend")
    public Result<List<KbDocVO>> recommend(@RequestParam(value = "limit", required = false) Integer limit) {
        return kbDocService.recommend(limit);
    }

    /** KB02 详情：正文 + 附件信息 + 是否已收藏；浏览数原子 +1 */
    @GetMapping("/docs/{id}")
    public Result<KbDocDetailVO> detail(@PathVariable("id") Long id) {
        return kbDocService.detail(id);
    }

    /** KB04 AI 提示词列表：聊天界面一键复用的数据源 */
    @GetMapping("/prompts")
    public Result<List<KbPromptVO>> prompts() {
        return kbDocService.prompts();
    }

    /** KB12 发布/存草稿（管理员；幂等键） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "KB_DOC_CREATE")
    @AuditLog(bizType = "KB", label = "发布知识库文档",
            summary = "'发布文档：' + #dto.title")
    @PostMapping("/docs")
    public Result<KbDocVO> create(@Valid @RequestBody KbDocCreateDTO dto) {
        return kbDocService.create(dto);
    }

    /** KB13 编辑全字段（管理员）；body 带 updatedAt 时启用乐观锁（1007） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "KB", label = "编辑知识库文档",
            summary = "'编辑文档：' + #id")
    @PutMapping("/docs/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody KbDocUpdateDTO dto) {
        return kbDocService.update(id, dto);
    }

    /** KB14 逻辑删除（管理员）；评论收藏不级联清 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "KB", label = "删除知识库文档",
            summary = "'删除文档：' + #id")
    @DeleteMapping("/docs/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return kbDocService.delete(id);
    }

    /** KB15 置顶/取消置顶（管理员）：原子翻转，重复点击效果稳定 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "KB", label = "置顶切换",
            summary = "'置顶切换：' + #id")
    @PutMapping("/docs/{id}/top")
    public Result<Void> toggleTop(@PathVariable("id") Long id) {
        return kbDocService.toggleTop(id);
    }

    /** KB16 上架/下架/发布草稿（管理员）：条件更新防并发跳档 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "KB", label = "文档状态流转",
            summary = "'状态流转：' + #id + ' → ' + #dto.status")
    @PutMapping("/docs/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @Valid @RequestBody KbDocStatusDTO dto) {
        return kbDocService.updateStatus(id, dto.getStatus());
    }

    /** KB17 管理视角列表（管理员；含 DRAFT/OFFLINE，部门管理员限本部门） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/admin/docs")
    public Result<PageResult<KbDocVO>> adminList(KbAdminQueryDTO dto) {
        return kbDocService.adminList(dto);
    }
}
