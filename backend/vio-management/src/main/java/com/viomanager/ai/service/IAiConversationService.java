package com.viomanager.ai.service;

import com.viomanager.ai.dto.ConversationCreateDTO;
import com.viomanager.ai.vo.ConversationCreateVO;
import com.viomanager.ai.vo.ConversationVO;
import com.viomanager.ai.vo.MessageVO;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;

/**
 * AI 会话管理（接口清单 §4，AI03-07；2026-10-24 拍板落库实做）。
 * 数据边界：全部仅本人——他人会话与不存在同报 9101，不给试探空间。
 */
public interface IAiConversationService {

    /** AI03 新建会话：title 缺省"新对话"；返回新 id */
    Result<ConversationCreateVO> create(ConversationCreateDTO dto);

    /** AI04 我的会话列表：更新时间倒序分页 */
    Result<PageResult<ConversationVO>> page(int page, int size);

    /** AI05 消息历史：正序分页，仅 user / assistant 对话消息 */
    Result<PageResult<MessageVO>> messages(Long conversationId, int page, int size);

    /** AI06 改标题：仅本人，他人/不存在 → 9101 */
    Result<Void> rename(Long conversationId, String title);

    /** AI07 删除会话（逻辑删除连带消息按会话隔离自然不可见）：仅本人 */
    Result<Void> delete(Long conversationId);

    /** 会话是否属于当前登录人（AI01 chat 预检用，不抛异常） */
    boolean owned(Long conversationId);
}
