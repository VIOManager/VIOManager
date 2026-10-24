package com.viomanager.audit.service;

import com.viomanager.audit.vo.AuditBizTypeVO;
import com.viomanager.audit.vo.AuditLogDetailVO;
import com.viomanager.audit.vo.AuditLogVO;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计查询服务（AQ01-03，纯只读，写入侧由骨架 @AuditLog AOP 承担）。
 */
public interface IAuditQueryService {

    /**
     * AQ01 分页列表：keyword（姓名/登录名/摘要模糊）、bizType、success、时间范围，
     * 按发生时间倒序。部门管理员只见操作人所属组织在本部门子树内的日志。
     */
    Result<PageResult<AuditLogVO>> page(String keyword, String bizType, Integer success,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        int page, int size);

    /** AQ02 业务类型枚举（从日志表动态 distinct） */
    Result<List<AuditBizTypeVO>> bizTypes();

    /** AQ03 单条详情（含请求方法/路径/UA/参数摘要）；无权或不存在 → 1004 */
    Result<AuditLogDetailVO> detail(Long id);
}
