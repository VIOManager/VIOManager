package com.viomanager.reply.controller;

import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.reply.dto.ReplyCreateDTO;
import com.viomanager.reply.service.IReplyService;
import com.viomanager.reply.vo.ReplyItemVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 楼层接口（F05/F07/F10）。
 * 幂等口径（规范 §6）：回帖带 X-Idempotency-Key；楼层点赞为状态型天然幂等不带。
 */
@RestController
@RequestMapping("/api/forum/topics/{topicId}/replies")
public class ReplyController {

    @Resource
    private IReplyService replyService;

    /**
     * F05 楼层列表：楼层号升序分页，HIDDEN 楼层占位显示。
     */
    @GetMapping
    public Result<PageResult<ReplyItemVO>> list(
            @PathVariable("topicId") Long topicId,
            @RequestParam(required = false, defaultValue = "1") long page,
            @RequestParam(required = false, defaultValue = "20") long size) {
        return Result.ok(replyService.list(topicId, page, size));
    }

    /**
     * F07 回帖（幂等：一张表单一个键）。锁定禁回（9101）在服务层守卫。
     * Authorization 头原样下传给 forum_user 快照换名用。
     */
    @Idempotent(bizType = "FORUM_REPLY_CREATE")
    @PostMapping
    public Result<Map<String, Object>> create(
            @PathVariable("topicId") Long topicId,
            @Valid @RequestBody ReplyCreateDTO dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return Result.ok(replyService.create(topicId, dto, UserHolder.getUser(), authHeader));
    }
}
