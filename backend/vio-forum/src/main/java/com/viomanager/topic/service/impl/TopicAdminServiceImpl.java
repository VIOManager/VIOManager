package com.viomanager.topic.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ForumErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.topic.dto.TopicStatusDTO;
import com.viomanager.topic.dto.TopicUpdateDTO;
import com.viomanager.topic.entity.ForumTopic;
import com.viomanager.topic.mapper.ForumTopicMapper;
import com.viomanager.topic.service.ITopicAdminService;
import com.viomanager.support.TagCodec;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理档主题操作实现（F11 编辑 / F12 状态）。角色门在 Controller @RequireRole。
 */
@Service
public class TopicAdminServiceImpl implements ITopicAdminService {

    private static final Set<String> TOPIC_STATUSES = Set.of("NORMAL", "LOCKED", "HIDDEN");

    @Resource
    private ForumTopicMapper forumTopicMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long topicId, TopicUpdateDTO dto) {
        ForumTopic topic = requireTopic(topicId);
        forumTopicMapper.update(null, new UpdateWrapper<ForumTopic>()
                .eq("id", topicId)
                .set("title", dto.getTitle())
                .set("body", dto.getBodyText())
                .set("tags", TagCodec.join(dto.getTags())));
        return Map.of("id", topicId, "title", dto.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setStatus(Long topicId, TopicStatusDTO dto) {
        requireTopic(topicId);
        String to = dto.getTo();
        if (!TOPIC_STATUSES.contains(to)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "to 取值不合法：仅 NORMAL / LOCKED / HIDDEN");
        }
        forumTopicMapper.update(null, new UpdateWrapper<ForumTopic>()
                .eq("id", topicId)
                .set("status", to));
        return Map.of("id", topicId, "status", to);
    }

    /** 管理档守卫：主题须存在（HIDDEN 主题是恢复操作对象，对管理员可见） */
    private ForumTopic requireTopic(Long topicId) {
        ForumTopic topic = forumTopicMapper.selectById(topicId);
        if (topic == null) {
            throw new BizException(ForumErrorCode.TOPIC_NOT_FOUND);
        }
        return topic;
    }
}
