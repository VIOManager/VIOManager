package com.viomanager.reply.controller;

import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.result.Result;
import com.viomanager.reply.service.IReplyService;
import jakarta.annotation.Resource;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 楼层点赞接口（F10）。
 * 路径独立于 /api/forum/topics/**（契约：POST /api/forum/replies/{id}/like），单独成控制器。
 * 状态型天然幂等，不挂 @Idempotent。
 */
@RestController
@RequestMapping("/api/forum/replies")
public class ReplyLikeController {

    @Resource
    private IReplyService replyService;

    /**
     * F10 楼层点赞/取消。
     */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean liked = Boolean.TRUE.equals(body.get("liked"));
        return Result.ok(replyService.like(id, liked, UserHolder.getUser()));
    }
}
