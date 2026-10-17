package com.viomanager.topic.controller;

import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.topic.dto.TopicStatusDTO;
import com.viomanager.topic.dto.TopicUpdateDTO;
import com.viomanager.topic.service.ITopicAdminService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理档主题端点（F11/F12，2026-10-17 拍板：超管与部门管理员同权，论坛只有学生/管理两档）。
 */
@RestController
@RequestMapping("/api/forum/topics")
public class TopicAdminController {

    @Resource
    private ITopicAdminService topicAdminService;

    /** F11 编辑主题（标题/正文/标签） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "FORUM_TOPIC_ADMIN_UPDATE")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody TopicUpdateDTO dto) {
        return Result.ok(topicAdminService.update(id, dto));
    }

    /** F12 状态：删除=HIDDEN（软删）/ 恢复=NORMAL / 锁定与解锁=LOCKED⇄NORMAL */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "FORUM_TOPIC_ADMIN_STATUS")
    @PostMapping("/{id}/status")
    public Result<Map<String, Object>> setStatus(@PathVariable("id") Long id,
                                                 @Valid @RequestBody TopicStatusDTO dto) {
        return Result.ok(topicAdminService.setStatus(id, dto));
    }
}
