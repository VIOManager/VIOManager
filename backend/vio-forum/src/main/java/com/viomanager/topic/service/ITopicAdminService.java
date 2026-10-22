package com.viomanager.topic.service;

import com.viomanager.topic.dto.TopicStatusDTO;
import com.viomanager.topic.dto.TopicUpdateDTO;
import java.util.Map;

/**
 * F11/F12 管理档主题操作（2026-10-17 拍板：论坛权限仅学生/管理两档）。
 * 删除=HIDDEN 软删（列表/详情对学生不可见，管理员 includeHidden 可见可恢复）。
 */
public interface ITopicAdminService {

    /** F11 编辑主题：标题/正文/标签（换版块不在常规编辑范围） */
    Map<String, Object> update(Long topicId, TopicUpdateDTO dto);

    /** F12 状态设置：to ∈ {NORMAL, LOCKED, HIDDEN}，管理员三态自由流转 */
    Map<String, Object> setStatus(Long topicId, TopicStatusDTO dto);
}
