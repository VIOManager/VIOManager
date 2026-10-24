package com.viomanager.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.audit.entity.AuditLogEntry;
import com.viomanager.audit.vo.AuditBizTypeVO;
import com.viomanager.common.datascop.DataScope;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 审计日志读侧 Mapper（AQ01-03，仅管理员调用——未声明 @DataScope 的方法
 * 对超管/部门管理员放行，学生被默认拒绝，与控制器 @RequireRole 双保险）。
 */
@Mapper
public interface AuditLogEntryMapper extends BaseMapper<AuditLogEntry> {

    /**
     * AQ02 业务类型枚举：从日志表 distinct 出现过的类型（类型 + 中文名）。
     * 后端各线新挂 @AuditLog 类型后这里自动出现，前端筛选下拉零改动。
     * 按类型最早出现时间排序（越早上线的类型越靠前）。
     */
    @DataScope(scope = DataScope.Scope.ALL)
    @Select("SELECT biz_type AS bizType, MAX(biz_type_label) AS label "
            + "FROM audit_log GROUP BY biz_type ORDER BY MIN(created_at)")
    List<AuditBizTypeVO> selectBizTypes();
}
