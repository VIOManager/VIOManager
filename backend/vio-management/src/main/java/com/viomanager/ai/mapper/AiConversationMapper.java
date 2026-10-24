package com.viomanager.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.ai.entity.AiConversation;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话 Mapper（AI03-07 会话管理 + AI01 会话模式读写）。
 * 数据边界在 service 层以 userId 过滤（仅本人）——SELF 语义：SQL 自带本人条件，各角色放行不注入；
 * 不声明则学生走 DataScopeInterceptor 默认拒（漏写本人条件的兜底保护）。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
