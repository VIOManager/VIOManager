package com.viomanager.org.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.org.dto.OrgSaveDTO;
import com.viomanager.org.service.OrgService;
import com.viomanager.org.vo.CollegeVO;
import com.viomanager.org.vo.OrgDetailVO;
import com.viomanager.org.vo.OrgNodeVO;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织管理接口（A 线契约 O01-O06）：路径 /api/orgs，与前端 org.ts 一一对应。
 * 两层权限：注解管"管理员可进"；子树数据范围（部门管理员）在服务层裁剪。
 * 写操作按规范 §6 挂幂等键、按规范挂审计。
 */
@RestController
@RequestMapping("/api/orgs")
public class OrgController {

    @Resource
    private OrgService orgService;

    /** O01 组织树：超管全平台，部门管理员仅本部门子树 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/tree")
    public Result<List<OrgNodeVO>> tree() {
        return Result.ok(orgService.tree());
    }

    /** O06 学院-专业静态映射：登录即可（全站学院下拉统一来源，学生表单也用） */
    @GetMapping("/colleges")
    public Result<List<CollegeVO>> colleges() {
        return Result.ok(orgService.colleges());
    }

    /** O05 组织详情 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/{id}")
    public Result<OrgDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(orgService.detail(id));
    }

    /** O02 创建组织 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "ORG_CREATE")
    @AuditLog(bizType = "ORG", label = "创建组织")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OrgSaveDTO dto) {
        return Result.ok(orgService.create(dto));
    }

    /** O03 编辑组织（改上级防循环 3006，子树 path/level 级联重算） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "ORG_UPDATE")
    @AuditLog(bizType = "ORG", label = "编辑组织")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @Valid @RequestBody OrgSaveDTO dto) {
        orgService.update(id, dto);
        return Result.ok();
    }

    /** O04 删除组织（有子组织 3007，人员 org_id 置空） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "ORG_DELETE")
    @AuditLog(bizType = "ORG", label = "删除组织")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        orgService.delete(id);
        return Result.ok();
    }
}
