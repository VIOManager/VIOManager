package com.viomanager.audit.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.audit.entity.AuditLogEntry;
import com.viomanager.audit.mapper.AuditLogEntryMapper;
import com.viomanager.audit.service.IAuditQueryService;
import com.viomanager.audit.vo.AuditBizTypeVO;
import com.viomanager.audit.vo.AuditLogDetailVO;
import com.viomanager.audit.vo.AuditLogVO;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.datascop.OrgScopeSupport;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 审计查询实现（AQ01-03）。
 * 数据范围：超管看全部；部门管理员按操作人组织在本部门子树内过滤（audit_log.org_id IN 子树，
 * 口径同审批中心先例——OrgScopeSupport 严格版，未配组织宁可误拒）。
 */
@Service
public class AuditQueryServiceImpl implements IAuditQueryService {

    /** 列表页宽列裁剪（2026-10-20 组长 SQL 规矩）：详情才有的列不进分页查询 */
    private static final String[] LIST_COLUMNS = {
            "id", "login_name", "real_name", "role", "ip",
            "biz_type", "biz_type_label", "summary", "success", "error_message", "cost_millis", "created_at"};

    @Resource
    private AuditLogEntryMapper auditLogEntryMapper;

    @Resource
    private OrgScopeSupport orgScopeSupport;

    @Override
    public Result<PageResult<AuditLogVO>> page(String keyword, String bizType, Integer success,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               int page, int size) {
        QueryWrapper<AuditLogEntry> qw = new QueryWrapper<>();
        qw.select(LIST_COLUMNS);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("real_name", kw).or().like("login_name", kw).or().like("summary", kw));
        }
        qw.eq(bizType != null && !bizType.isBlank(), "biz_type", bizType);
        qw.eq(success != null, "success", success);
        qw.ge(startTime != null, "created_at", startTime);
        qw.le(endTime != null, "created_at", endTime);
        applyDeptScope(qw);
        qw.orderByDesc("created_at").orderByDesc("id");

        Page<AuditLogEntry> result = auditLogEntryMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), qw);
        List<AuditLogVO> list = new ArrayList<>(result.getRecords().size());
        for (AuditLogEntry entry : result.getRecords()) {
            list.add(BeanUtil.copyProperties(entry, AuditLogVO.class));
        }
        return Result.ok(PageResult.of(list, result.getTotal(), result.getCurrent(), result.getSize()));
    }

    @Override
    public Result<List<AuditBizTypeVO>> bizTypes() {
        return Result.ok(auditLogEntryMapper.selectBizTypes());
    }

    @Override
    public Result<AuditLogDetailVO> detail(Long id) {
        AuditLogEntry entry = auditLogEntryMapper.selectById(id);
        if (entry == null || !inDeptScope(entry)) {
            //不存在与越权统一回 1004：不给部门管理员探测其他部门日志存在性的口子
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        return Result.ok(BeanUtil.copyProperties(entry, AuditLogDetailVO.class));
    }

    /** 部门管理员注入子树过滤；超管不过滤。学生进不来（控制器 @RequireRole） */
    private void applyDeptScope(QueryWrapper<AuditLogEntry> qw) {
        CurrentUser user = UserHolder.getUser();
        if (!"SUPER_ADMIN".equals(user.getRole())) {
            qw.in("org_id", orgScopeSupport.subtreeIds(user.getOrgId()));
        }
    }

    /** 详情的单条越权判定：部门管理员只能看子树内操作人的日志（操作人无组织 = 不在子树，拒绝） */
    private boolean inDeptScope(AuditLogEntry entry) {
        CurrentUser user = UserHolder.getUser();
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return true;
        }
        return orgScopeSupport.inSubtree(user.getOrgId(), entry.getOrgId());
    }
}
