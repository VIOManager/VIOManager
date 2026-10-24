package com.viomanager.audit.controller;

import com.viomanager.audit.service.IAuditQueryService;
import com.viomanager.audit.vo.AuditBizTypeVO;
import com.viomanager.audit.vo.AuditLogDetailVO;
import com.viomanager.audit.vo.AuditLogVO;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计查询接口（清单 AQ01-03）：给管理员一个查账页面，纯只读。
 * 仅超管与部门管理员（学生 1003）；部门管理员按操作人组织子树过滤（服务层）。
 * 时间参数走查询串（不走 Jackson），显式声明规范格式 yyyy-MM-dd HH:mm:ss。
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditQueryController {

    @Resource
    private IAuditQueryService auditQueryService;

    /**
     * AQ01 分页列表。筛选：keyword（姓名/登录名/摘要模糊）、bizType、success（1 成功 0 失败）、
     * startTime/endTime（yyyy-MM-dd HH:mm:ss）。按发生时间倒序；request_params 列表不返回。
     */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<PageResult<AuditLogVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditQueryService.page(keyword, bizType, success, startTime, endTime, page, size);
    }

    /** AQ02 业务类型枚举（筛选下拉用）：从日志表动态 distinct，后端加类型前端零改动 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/biz-types")
    public Result<List<AuditBizTypeVO>> bizTypes() {
        return auditQueryService.bizTypes();
    }

    /** AQ03 单条详情：列表字段 + httpMethod/uri/userAgent/requestParams，排查失败用 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/{id}")
    public Result<AuditLogDetailVO> detail(@PathVariable("id") Long id) {
        return auditQueryService.detail(id);
    }
}
