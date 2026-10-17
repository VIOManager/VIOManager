package com.viomanager.topic.controller;

import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.topic.dto.TopicCreateDTO;
import com.viomanager.topic.service.ITopicCommandService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主题命令接口（F06/F08/F09）。
 * 幂等口径（规范 §6）：发帖带 X-Idempotency-Key；点赞/收藏为状态型天然幂等不带。
 */
@RestController
@RequestMapping("/api/forum/topics")
public class TopicCommandController {

    @Resource
    private ITopicCommandService topicCommandService;

    /**
     * F06 发帖（幂等：一张表单一个键，失败重试复用同键）。
     * Authorization 头原样下传，给 forum_user 快照换名用（方案 §二.3）。
     */
    @Idempotent(bizType = "FORUM_TOPIC_CREATE")
    @PostMapping
    public Result<Map<String, Object>> create(
            @Valid @RequestBody TopicCreateDTO dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return Result.ok(topicCommandService.create(dto, UserHolder.getUser(), authHeader));
    }

    /**
     * F08 帖子点赞/取消（状态型天然幂等）。
     */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean liked = Boolean.TRUE.equals(body.get("liked"));
        return Result.ok(topicCommandService.like(id, liked, UserHolder.getUser()));
    }

    /**
     * F09 帖子收藏/取消（状态型天然幂等）。
     */
    @PostMapping("/{id}/collect")
    public Result<Map<String, Object>> collect(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean collected = Boolean.TRUE.equals(body.get("collected"));
        return Result.ok(topicCommandService.collect(id, collected, UserHolder.getUser()));
    }
}
