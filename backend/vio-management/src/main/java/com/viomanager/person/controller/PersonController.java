package com.viomanager.person.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.person.dto.MemberOptionVO;
import com.viomanager.person.dto.PersonAccountDTO;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonFileBindDTO;
import com.viomanager.person.dto.PersonIdCardVO;
import com.viomanager.person.dto.PersonImportVO;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonSkillsDTO;
import com.viomanager.person.dto.PersonUpdateDTO;
import com.viomanager.person.dto.PersonVO;
import com.viomanager.person.service.PersonService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 人员档案接口（A 线契约 M01–M13）：路径 /api/members，与前端 member.ts 一一对应。
 * 职责边界：本类只做权限收口（@RequireRole）、参数接收（@Valid）、调 service——
 * 不写查询、不写脱敏，业务在 PersonServiceImpl 与 support 包。
 * 两层权限：注解管"哪些角色能进"；视角裁剪（M02 四档）、部门管理员子树封堵在服务层。
 * 敏感动作全写审计：M10/M11 查看身份证与证件照签名、M13 账号操作；含身份证入参的写接口
 * 标 sensitive=true（参数不落审计库）。
 */
@RestController
@RequestMapping("/api/members")
public class PersonController {

    @Resource
    private PersonService personService;

    /** M01 分页列表：管理员用；部门管理员服务端自动叠加组织子树过滤 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<PageResult<PersonVO>> page(@Valid PersonQueryDTO dto) {
        return personService.page(dto);
    }

    /** M14 受限候选搜索（队长流程用）：任意登录用户可调（同 M02 口径不在此处限角色），
     *  出参仅目录级七字段无任何敏感列；B 线队长端加载预填成员/指导教师候选专用
     *  （原先直调 M01 必 403，2026-10-23 增此受限口）。字面路径优先于 /{id} 模板匹配。 */
    @GetMapping("/options")
    public Result<PageResult<MemberOptionVO>> options(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "memberType", required = false) String memberType,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        return personService.options(keyword, memberType, page, size);
    }

    /** M02 详情：任意登录用户可调，出参按四档视角裁剪（服务层），不在此处限角色 */
    @GetMapping("/{id}")
    public Result<PersonVO> detail(@PathVariable("id") Long id) {
        return personService.detail(id);
    }

    /** M03 手工新建（管理员）；学号可空不可重复（3001），身份证服务端加密入库 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERSON_CREATE")
    @AuditLog(bizType = "PERSON_CREATE", label = "新增人员", sensitive = true,
            summary = "'新增人员：' + #dto.name")
    @PostMapping
    public Result<PersonVO> create(@Valid @RequestBody PersonCreateDTO dto) {
        return personService.create(dto);
    }

    /** M04 编辑（管理员）；入参不收学号（不可修改，3002 占位），null 字段不改 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERSON_UPDATE")
    @AuditLog(bizType = "PERSON_UPDATE", label = "编辑人员", sensitive = true,
            summary = "'编辑人员：' + #id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody PersonUpdateDTO dto) {
        personService.update(id, dto);
        return Result.ok();
    }

    /** M05 删除（管理员）：队长拒删（3003）、当前登录账号拒删（3004）、清队伍成员关系 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERSON_DELETE")
    @AuditLog(bizType = "PERSON_DELETE", label = "删除人员",
            summary = "'删除人员：' + #id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        personService.delete(id);
        return Result.ok();
    }

    /** M06 导入模板下载（管理员）：14 列 xlsx，与 M07 导入列一一对应 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) {
        personService.importTemplate(response);
    }

    /** M07 Excel 批量导入（管理员）：组织按名称匹配；学号已存在的行跳过；逐行返回错误 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERSON_IMPORT")
    @AuditLog(bizType = "PERSON_IMPORT", label = "人员导入", sensitive = true,
            summary = "'导入人员 Excel：' + #file.originalFilename")
    @PostMapping("/import")
    public Result<PersonImportVO> importExcel(@RequestParam("file") MultipartFile file) {
        return personService.importExcel(file);
    }

    /** M08 证件照回填（管理员）：body 传 fileId（先走 F01 上传），不是 MultipartFile 直传 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERSON_PHOTO")
    @AuditLog(bizType = "PERSON_PHOTO", label = "绑定证件照",
            summary = "'绑定证件照：人员' + #id")
    @PostMapping("/{id}/photo")
    public Result<Void> bindPhoto(@PathVariable("id") Long id,
                                  @Valid @RequestBody PersonFileBindDTO dto) {
        personService.bindPhoto(id, dto);
        return Result.ok();
    }

    /** M09 电子签名回填（管理员）：同 M08 的 fileId 口径 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERSON_SIGNATURE")
    @AuditLog(bizType = "PERSON_SIGNATURE", label = "绑定电子签名",
            summary = "'绑定电子签名：人员' + #id")
    @PostMapping("/{id}/signature")
    public Result<Void> bindSignature(@PathVariable("id") Long id,
                                      @Valid @RequestBody PersonFileBindDTO dto) {
        personService.bindSignature(id, dto);
        return Result.ok();
    }

    /** M10 完整身份证号：超管/本部门管理员/本队队长/本人（3010），每次查看写审计 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "MEMBER_ID_CARD", label = "查看身份证",
            summary = "'查看身份证：人员' + #id")
    @GetMapping("/{id}/id-card")
    public Result<PersonIdCardVO> idCard(@PathVariable("id") Long id) {
        return personService.idCard(id);
    }

    /** M11a 证件照下载：权限同 M10，写审计；文件本体走文件服务的病毒闸门与下载计数 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "MEMBER_PHOTO_VIEW", label = "下载证件照",
            summary = "'下载证件照：人员' + #id")
    @GetMapping("/{id}/photo")
    public void downloadPhoto(@PathVariable("id") Long id, HttpServletResponse response) {
        personService.downloadPhoto(id, response);
    }

    /** M11b 电子签名下载：权限同 M10，写审计 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "MEMBER_SIGNATURE_VIEW", label = "下载电子签名",
            summary = "'下载电子签名：人员' + #id")
    @GetMapping("/{id}/signature")
    public void downloadSignature(@PathVariable("id") Long id, HttpServletResponse response) {
        personService.downloadSignature(id, response);
    }

    /** M12 技能标签整组替换：本人或管理员（服务层判定），值必须来自 SKILL 字典 */
    @AuditLog(bizType = "MEMBER_SKILLS", label = "更新技能标签",
            summary = "'更新技能：人员' + #id")
    @PutMapping("/{id}/skills")
    public Result<Void> updateSkills(@PathVariable("id") Long id,
                                     @Valid @RequestBody PersonSkillsDTO dto) {
        personService.updateSkills(id, dto.getSkills());
        return Result.ok();
    }

    /** M13 绑定登录账号（管理员）：登录名缺省取学工号，初始密码 abc123、首次登录强制改密 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERSON_ACCOUNT")
    @AuditLog(bizType = "PERSON_ACCOUNT", label = "绑定登录账号",
            summary = "'绑定账号：人员' + #id")
    @PostMapping("/{id}/account")
    public Result<Void> bindAccount(@PathVariable("id") Long id,
                                    @Valid @RequestBody PersonAccountDTO dto) {
        personService.bindAccount(id, dto);
        return Result.ok();
    }

    /** M13 修改登录账号（管理员）：改登录名 / 重置密码（重置后旧 token 全部失效） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERSON_ACCOUNT")
    @AuditLog(bizType = "PERSON_ACCOUNT", label = "修改登录账号",
            summary = "'修改账号：人员' + #id")
    @PutMapping("/{id}/account")
    public Result<Void> changeAccount(@PathVariable("id") Long id,
                                      @Valid @RequestBody PersonAccountDTO dto) {
        personService.changeAccount(id, dto);
        return Result.ok();
    }

    /** Excel 导出（契约外补充接口，仅超管）：待登记进 A 线接口清单（需 lzh 拍板） */
    @RequireRole("SUPER_ADMIN")
    @AuditLog(bizType = "PERSON_EXPORT", label = "人员导出")
    @GetMapping("/export")
    public void export(@Valid PersonQueryDTO dto, HttpServletResponse response) {
        personService.export(dto, response);
    }
}
