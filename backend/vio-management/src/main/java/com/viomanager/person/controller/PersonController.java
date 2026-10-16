package com.viomanager.person.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.person.dto.PersonCreateDTO;
import com.viomanager.person.dto.PersonQueryDTO;
import com.viomanager.person.dto.PersonUpdateDTO;
import com.viomanager.person.dto.PersonVO;
import com.viomanager.person.service.PersonService;
import com.viomanager.person.support.PersonExcelSupport;
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
 * 人员档案接口（P01–P08）：超管后台管理平台人员档案的入口。
 * 职责边界：本类只做权限收口（@RequireRole）、参数接收（@Valid）、调 service、封装返回——
 * 不写数据库查询、不写脱敏逻辑，业务全部在 PersonServiceImpl。
 * 权限两层：注解管"只有超管能进接口"；列表/详情/导出的敏感字段脱敏在 service 按角色完成。
 * 含身份证等敏感参数的写接口审计标 sensitive=true，参数不落审计库。
 */
@RestController
@RequestMapping("/api/persons")
public class PersonController {

    @Resource
    private PersonService personService;

    /** P01 新增人员 */
    @RequireRole("SUPER_ADMIN")
    @Idempotent(bizType = "PERSON_CREATE")
    @AuditLog(bizType = "PERSON_CREATE", label = "新增人员", sensitive = true,
            summary = "'新增人员：' + #dto.name")
    @PostMapping
    public Result<PersonVO> create(@Valid @RequestBody PersonCreateDTO dto) {
        return personService.create(dto);
    }

    /** P02 编辑人员档案 */
    @RequireRole("SUPER_ADMIN")
    @Idempotent(bizType = "PERSON_UPDATE")
    @AuditLog(bizType = "PERSON_UPDATE", label = "编辑人员", sensitive = true,
            summary = "'编辑人员：' + #id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody PersonUpdateDTO dto) {
        personService.update(id, dto);
        return Result.ok();
    }

    /** P03 删除人员档案（逻辑删除） */
    @RequireRole("SUPER_ADMIN")
    @Idempotent(bizType = "PERSON_DELETE")
    @AuditLog(bizType = "PERSON_DELETE", label = "删除人员",
            summary = "'删除人员：' + #id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        personService.delete(id);
        return Result.ok();
    }

    /** P04 分页查询：按筛选条件查列表，出参按角色自动脱敏 */
    @RequireRole("SUPER_ADMIN")
    @GetMapping
    public Result<PageResult<PersonVO>> page(@Valid PersonQueryDTO dto) {
        return personService.page(dto);
    }

    /** P05 人员详情：完整档案，按当前登录角色脱敏 */
    @RequireRole("SUPER_ADMIN")
    @GetMapping("/{id}")
    public Result<PersonVO> detail(@PathVariable("id") Long id) {
        return personService.detail(id);
    }

    /** P06 Excel 批量导入：解析逐行校验入库，返回总行数/成功数/逐行错误 */
    @RequireRole("SUPER_ADMIN")
    @Idempotent(bizType = "PERSON_IMPORT")
    @AuditLog(bizType = "PERSON_IMPORT", label = "人员导入", sensitive = true,
            summary = "'导入人员 Excel：' + #file.originalFilename")
    @PostMapping("/import")
    public Result<PersonExcelSupport.ImportResult> importExcel(
            @RequestParam("file") MultipartFile file) {
        return personService.importExcel(file);
    }

    /** P07 Excel 导出：按筛选条件导出档案文件（仅超管可调，导出完整敏感信息） */
    @RequireRole("SUPER_ADMIN")
    @AuditLog(bizType = "PERSON_EXPORT", label = "人员导出")
    @GetMapping("/export")
    public void export(@Valid PersonQueryDTO dto, HttpServletResponse response) {
        personService.export(dto, response);
    }

    /** P08 证件照上传：复用文件模块上传拿 fileId，绑定到人员档案 */
    @RequireRole("SUPER_ADMIN")
    @Idempotent(bizType = "PERSON_PHOTO")
    @AuditLog(bizType = "PERSON_PHOTO", label = "上传证件照",
            summary = "'上传证件照：人员' + #id")
    @PostMapping("/{id}/photo")
    public Result<Void> uploadPhoto(@PathVariable("id") Long id,
                                    @RequestParam("file") MultipartFile file) {
        personService.bindPhoto(id, file);
        return Result.ok();
    }
}
