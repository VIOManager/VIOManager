package com.viomanager.topic.service;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.topic.dto.TopicCreateDTO;
import java.util.Map;

/**
 * 主题命令服务（F06/F08/F09）。
 */
public interface ITopicCommandService {

    /**
     * F06 发帖：版块守卫（9104）→ 快照冻结 → 建主题（置顶仅超管生效）。
     * 幂等在控制器层挂（FORUM_TOPIC_CREATE），服务只管业务。
     */
    Map<String, Object> create(TopicCreateDTO dto, CurrentUser user, String authHeader);

    /**
     * F08 点赞/取消（状态型天然幂等）：uk 撞键视为已赞不重复计数，取消走硬删。
     */
    Map<String, Object> like(Long topicId, boolean liked, CurrentUser user);

    /**
     * F09 收藏/取消（同点赞口径）。
     */
    Map<String, Object> collect(Long topicId, boolean collected, CurrentUser user);
}
