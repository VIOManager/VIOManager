package com.viomanager.ai.service;

import com.viomanager.ai.entity.AiConversation;
import com.viomanager.ai.entity.AiMessage;
import com.viomanager.ai.mapper.AiConversationMapper;
import com.viomanager.ai.mapper.AiMessageMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 聊天落库器（会话模式）：user 提问与 assistant 回复在流结束后统一写入，
 * 生成失败且无产出时不落——库里不出现"问了没答"的孤儿提问（重新生成由前端重发）。
 * 中断语义（清单 §5）：客户端断开/超限截断 → 已生成部分照常落库，incomplete=1。
 * 两条 insert 非同一事务：单机单人写自己的会话，无并发对写场景，不做跨行原子（注释存照）。
 */
@Slf4j
@Component
public class AiChatRecorder {

    /** 首问自动改标题的截取长度（清单 §4：首问前 20 字） */
    private static final int TITLE_LENGTH = 20;

    private static final String DEFAULT_TITLE = "新对话";

    @Resource
    private AiMessageMapper messageMapper;

    @Resource
    private AiConversationMapper conversationMapper;

    /**
     * 流结束后调用。assistantReply 为空（上游失败且无产出）时只回退不落任何消息。
     */
    public void record(AiConversation conversation, String userContent, String assistantReply,
                       int assistantIncomplete, Long promptTokens, Long completionTokens,
                       long costMillis) {
        try {
            int added = 1;
            messageMapper.insert(new AiMessage()
                    .setConversationId(conversation.getId())
                    .setRole("user")
                    .setContent(userContent)
                    .setIncomplete(0));
            if (assistantReply != null && !assistantReply.isEmpty()) {
                added++;
                messageMapper.insert(new AiMessage()
                        .setConversationId(conversation.getId())
                        .setRole("assistant")
                        .setContent(assistantReply)
                        .setIncomplete(assistantIncomplete)
                        .setPromptTokens(promptTokens == null ? null : promptTokens.intValue())
                        .setCompletionTokens(completionTokens == null ? null : completionTokens.intValue())
                        .setCostMillis(costMillis));
            }
            conversation.setMessageCount(
                    (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + added);
            conversation.setLastMessageAt(LocalDateTime.now());
            if (DEFAULT_TITLE.equals(conversation.getTitle()) && userContent != null && !userContent.isBlank()) {
                conversation.setTitle(userContent.length() > TITLE_LENGTH
                        ? userContent.substring(0, TITLE_LENGTH) : userContent);
            }
            conversationMapper.updateById(conversation);
        } catch (Exception e) {
            //落库失败不影响已发出的回复流；仅记日志（chat 主链路已结束，无补偿路径可走）
            log.error("AI 会话落库失败：conversationId={}, 原因={}", conversation.getId(), e.getMessage());
        }
    }
}
