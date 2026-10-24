package com.viomanager.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.ai.entity.AiMessage;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消息 Mapper（AI05 历史 + AI01 聊天落库）。
 * 本表无 org_id，访问必经会话的本人校验（service 层 requireOwn）——SELF 自管。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
