package com.viomanager.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.ai.config.AiProperties;
import com.viomanager.ai.dto.ConversationCreateDTO;
import com.viomanager.ai.entity.AiConversation;
import com.viomanager.ai.entity.AiMessage;
import com.viomanager.ai.mapper.AiConversationMapper;
import com.viomanager.ai.mapper.AiMessageMapper;
import com.viomanager.ai.service.IAiConversationService;
import com.viomanager.ai.vo.ConversationCreateVO;
import com.viomanager.ai.vo.ConversationVO;
import com.viomanager.ai.vo.MessageVO;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.AiErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * AI 会话管理实现（AI03-07）。
 * 口径：所有读写先以 id + userId 双条件定位——"不存在"与"非本人"合并 9101；
 * 审计只在 chat 挂（清单 §6），会话 CRUD 是个人低敏操作不挂（组长线先例：dict 条目个人补充也不全挂）。
 */
@Service
public class AiConversationServiceImpl implements IAiConversationService {

    /** 应用层 provider 定值（拍板 GLM；列默认 'ollama' 是建表契约遗留，不依赖） */
    private static final String PROVIDER_GLM = "glm";

    private static final String DEFAULT_TITLE = "新对话";

    @Resource
    private AiConversationMapper conversationMapper;

    @Resource
    private AiMessageMapper messageMapper;

    @Resource
    private AiProperties aiProperties;

    @Override
    public Result<ConversationCreateVO> create(ConversationCreateDTO dto) {
        CurrentUser user = UserHolder.getUser();
        AiConversation conversation = new AiConversation()
                .setTitle(dto != null && dto.getTitle() != null && !dto.getTitle().isBlank()
                        ? dto.getTitle().trim() : DEFAULT_TITLE)
                .setUserId(user.getUserId())
                //orgId 从 token 冗余（部门管理员有值）；memberId 待工具调用域再按需回填
                .setOrgId(user.getOrgId())
                .setProvider(PROVIDER_GLM)
                .setModel(aiProperties.getModel())
                .setMessageCount(0)
                .setArchived(0);
        conversationMapper.insert(conversation);
        ConversationCreateVO vo = new ConversationCreateVO();
        vo.setId(conversation.getId());
        return Result.ok(vo);
    }

    @Override
    public Result<PageResult<ConversationVO>> page(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<AiConversation> pager = new Page<>(safePage, safeSize);
        Page<AiConversation> result = conversationMapper.selectPage(pager,
                new QueryWrapper<AiConversation>()
                        .eq("user_id", UserHolder.getUser().getUserId())
                        .orderByDesc("updated_at"));
        List<ConversationVO> list = result.getRecords().stream().map(c -> {
            ConversationVO vo = new ConversationVO();
            vo.setId(c.getId());
            vo.setTitle(c.getTitle());
            vo.setModel(c.getModel());
            vo.setMessageCount(c.getMessageCount());
            vo.setLastMessageAt(c.getLastMessageAt());
            vo.setCreatedAt(c.getCreatedAt());
            return vo;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), safePage, safeSize));
    }

    @Override
    public Result<PageResult<MessageVO>> messages(Long conversationId, int page, int size) {
        requireOwn(conversationId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<AiMessage> pager = new Page<>(safePage, safeSize);
        Page<AiMessage> result = messageMapper.selectPage(pager,
                new QueryWrapper<AiMessage>()
                        .eq("conversation_id", conversationId)
                        .in("role", "user", "assistant")
                        .orderByAsc("created_at"));
        List<MessageVO> list = result.getRecords().stream().map(m -> {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setIncomplete(m.getIncomplete());
            vo.setCreatedAt(m.getCreatedAt());
            return vo;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), safePage, safeSize));
    }

    @Override
    public Result<Void> rename(Long conversationId, String title) {
        AiConversation conversation = requireOwn(conversationId);
        conversation.setTitle(title.trim());
        conversationMapper.updateById(conversation);
        return Result.ok();
    }

    @Override
    public Result<Void> delete(Long conversationId) {
        requireOwn(conversationId);
        //@TableLogic：deleteById 转 UPDATE deleted=1；消息行不动，随会话隔离自然不可见
        conversationMapper.deleteById(conversationId);
        return Result.ok();
    }

    /** id + 本人双条件定位；查不到（不存在或他人）一律 9101 */
    private AiConversation requireOwn(Long conversationId) {
        AiConversation conversation = conversationMapper.selectOne(
                new QueryWrapper<AiConversation>()
                        .eq("id", conversationId)
                        .eq("user_id", UserHolder.getUser().getUserId()));
        if (conversation == null) {
            throw new BizException(AiErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }
}
