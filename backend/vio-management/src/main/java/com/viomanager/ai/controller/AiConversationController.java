package com.viomanager.ai.controller;

import com.viomanager.ai.dto.ConversationCreateDTO;
import com.viomanager.ai.dto.ConversationRenameDTO;
import com.viomanager.ai.service.IAiConversationService;
import com.viomanager.ai.vo.ConversationCreateVO;
import com.viomanager.ai.vo.ConversationVO;
import com.viomanager.ai.vo.MessageVO;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 会话管理接口（清单 §4，AI03-07；2026-10-24 拍板落库实做）。
 * 三端共用、数据边界全在 service 层以 userId 收口，不挂 @RequireRole；
 * 审计只在 AI01 chat 挂（清单 §6），会话 CRUD 为个人低敏操作。
 */
@RestController
@RequestMapping("/api/ai/conversations")
public class AiConversationController {

    @Resource
    private IAiConversationService conversationService;

    /** AI03 新建会话：幂等键防双击；body 可省略，title 缺省"新对话" */
    @Idempotent(bizType = "AI_CONVERSATION_CREATE")
    @PostMapping
    public Result<ConversationCreateVO> create(@Valid @RequestBody(required = false) ConversationCreateDTO dto) {
        return conversationService.create(dto);
    }

    /** AI04 我的会话列表：更新时间倒序，分页 */
    @GetMapping
    public Result<PageResult<ConversationVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return conversationService.page(page, size);
    }

    /** AI05 消息历史：正序分页；他人会话/不存在 → 9101 */
    @GetMapping("/{id}/messages")
    public Result<PageResult<MessageVO>> messages(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return conversationService.messages(id, page, size);
    }

    /** AI06 改标题：仅本人 */
    @PatchMapping("/{id}")
    public Result<Void> rename(@PathVariable("id") Long id, @Valid @RequestBody ConversationRenameDTO dto) {
        return conversationService.rename(id, dto.getTitle());
    }

    /** AI07 删除会话（逻辑删除）：仅本人 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return conversationService.delete(id);
    }
}
